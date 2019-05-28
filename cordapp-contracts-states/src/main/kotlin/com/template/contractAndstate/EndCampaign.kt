package com.template.contractAndstate

import co.paralleluniverse.fibers.Suspendable
import com.template.*
import com.template.CampaignContract
import net.corda.core.contracts.*
import net.corda.core.crypto.keys
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.unwrap
import net.corda.finance.DOLLARS
import net.corda.finance.USD

import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.getCashBalance
import net.corda.finance.utils.sumCash
import net.corda.core.contracts.Amount.Companion.sumOrZero
import net.corda.core.utilities.loggerFor
import net.corda.finance.flows.CashPaymentFlow
import net.corda.nodeapi.internal.serialization.attachmentsClassLoaderEnabledPropertyName

const val CASH_PROGRAM_ID: ContractClassName = "net.corda.finance.contracts.asset.Cash"

// *********
// * Flows *
// *********

object EndCampaign{
    @SchedulableFlow
    @InitiatingFlow
    /**The campaign ending will automatically execute when the campaign deadline has passed*/
    class Initiator(private val stateRef: StateRef): FlowLogic<SignedTransaction>() {
//        var x: Int = 1
        /**Request cash payload from donors*/
        @Suspendable
        fun requestDonatedCash(sessions: List<FlowSession>): CashStatesPayload{
            val cashStates = sessions.map { session ->
                //Sent Success message to all donors
                session.send(CampaignResult.Success(stateRef))
                //Resolve the transaction
                subFlow(ReceiveStateAndRefFlow<ContractState>(session))
                //Receive the cash inputs, outputs and keys
                session.receive<CashStatesPayload>().unwrap { it }
            }
            return CashStatesPayload(
                    cashStates.flatMap { it.inputs },
                    cashStates.flatMap { it.outputs },
                    cashStates.flatMap { it.signingKeys }
            )
        }

        private fun cancelDonation(campaign: Campaign): TransactionBuilder{
            // Pick a notary
            val notary: Party = serviceHub.networkMapCache.notaryIdentities.first()
            val utx = TransactionBuilder(notary = notary)

            //Create inputs
            val donationStateAndRefs = serviceHub.vaultService.queryBy<Donation>().states.filter { it.state.data.campaignReference == campaign.linearId }
            logger.info("donationStateAndRefs: $donationStateAndRefs")
            val campaignInputStateAndRef = serviceHub.toStateAndRef<Campaign>(stateRef)
            val campaignState = campaignInputStateAndRef.state.data
            //Create commands
            val endCampaignCommand = Command(CampaignContract.Commands.End(), campaign.fundraiser.owningKey)
            val cancelPledgeCommand = Command(DonationContract.Cancel(), campaign.fundraiser.owningKey)

            //Output State
            val campaignOutputState = campaignState.copy(status = "Out Of Date")
            val campaignOutputStateAndContract = TransactionState(campaignOutputState,CampaignContract.ID,notary,null)
            // Add all components
            donationStateAndRefs.forEach { utx.addInputState(it) } // input
            utx.addInputState(campaignInputStateAndRef) //input
            utx.addOutputState(campaignOutputStateAndContract) // output
            utx.addCommand(endCampaignCommand) // command
            utx.addCommand(cancelPledgeCommand) //command

            return utx
        }

        @Suspendable
        fun handleSuccess(campaign: Campaign,sessions: List<FlowSession>): TransactionBuilder{
            logger.info("handleSuccess1")
            val utx = cancelDonation(campaign)
            //Collect the cash states from donors
            val cashStates = requestDonatedCash(sessions)
            //Add the cash inputs, outputs and commands
            cashStates.inputs.forEach { utx.addInputState(it) }
            cashStates.outputs.forEach { utx.addOutputState(it, CASH_PROGRAM_ID) }
            utx.addCommand(Cash.Commands.Move(),cashStates.signingKeys)
            logger.info("handleSuccess2")
            return utx
        }

        @Suspendable
        override fun call(): SignedTransaction {

            //Get campaign data
            val campaign = serviceHub.loadState(stateRef).data as Campaign
            logger.info("End campaign campaign : $campaign")
            logger.info("*******************************")
            logger.info("ourIdentity: $ourIdentity")
            logger.info("fundraiser: ${campaign.fundraiser}")
            logger.info("================================")
            val donationsForCampaign = serviceHub.vaultService.queryBy<Donation>().states.filter { it.state.data.campaignReference == campaign.linearId }.map { it.state.data }
            logger.info("donationsForCampaign: $donationsForCampaign")
            //Only fundraiser can run this flow
            if (campaign.fundraiser != ourIdentity){
                throw FlowException("Only the fundraiser can run this flow.")
            }
            logger.info("Before Initiate Flow")

            //Create flow sessions for all donors
            val sessions = donationsForCampaign
                    .map { serviceHub.identityService.requireWellKnownPartyFromAnonymous(it.donor) }
                    .distinct().map { donor ->
            initiateFlow(donor)

            }
            logger.info("sessions.donor : $sessions")
            //logger.info("sessions: $sessions")
            logger.info("End campaign sessions: $sessions ")
            logger.info("After Initiate Flow")
            // End campaign whatever it success or not
            val utx = when {
                campaign.raised < campaign.target -> {
                    sessions.forEach { session -> session.send(CampaignResult.Failure()) }
                    logger.info("Failed")
//                    x = 0
                    cancelDonation(campaign)

                }
                else -> handleSuccess(campaign,sessions)

        }
            // Sign, finalise and broadcast the transaction to responder.
            logger.info("Before ptx")
            val ptx = serviceHub.signInitialTransaction(utx)
            logger.info("Endcampaign stx: $ptx")
            logger.info("Before stx")
            val stx = subFlow(CollectSignaturesFlow(ptx, sessions.map { it }))
            logger.info("Endcampaign stx: $stx")
            logger.info("sessionaa:${sessions.map { it }}")
            logger.info("After stx")
            val ftx = subFlow(FinalityFlow(stx))
            logger.info("Endcampaign Success")
            logger.info("CampaignStateRef: $stateRef")
//            logger.info("temp: $x")
//            if(x != 0)
            val receiptStateAndRefs = serviceHub.vaultService.queryBy<Receipt>().states.filter { it.state.data.campaignReference == campaign.linearId }.map { it.state.data }
            val token = receiptStateAndRefs.first().amount.token

            val transferAmountToRecipient = receiptStateAndRefs.map { it.amount }.sumOrZero(token)
            logger.info("transferAmountToRecipient: $transferAmountToRecipient")
            subFlow(CashPaymentFlow(transferAmountToRecipient,campaign.recipient))
//            subFlow(TransferFundToRecipient.Initiator(stateRef))
//            else logger.info("Not initiate transfer fund to recipient flow ")
            logger.info("TransferFundToRecipient Successfully")
            return ftx
    }


}

    @InitiatedBy(Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {
        // Get donation state for this campaign
        @Suspendable
        fun handleSuccess(campaignRef: StateRef) {
            logger.info("handle success responder")
            val campaign = serviceHub.loadState(campaignRef).data as Campaign
            logger.info("campaignaa: $campaign ")
            logger.info("otehrSession: $otherSession")
            val results = serviceHub.vaultService.queryBy<Donation>().states.filter { it.state.data.campaignReference == campaign.linearId }.map { it.state.data }
            logger.info("Responder ouridentity: $ourIdentity")
            logger.info("Responder results: $results")
            val token = results.first().amount.token
            val amount = results.map { it.amount }.sumOrZero(token)
            logger.info("Transfer amount: $amount")
            logger.info("After amount")
            val queryCash = serviceHub.getCashBalance(USD).toString()
            logger.info("Make donation CashBalance: $queryCash")
            val cash = serviceHub.vaultService.queryBy<Cash.State>().states
            logger.info("Make donation CashQuery: $cash")
            logger.info("generateSpend ouridentity: $ourIdentity")
            val (utx, _) = Cash.generateSpend(serviceHub, TransactionBuilder(), amount, ourIdentityAndCert,campaign.fundraiser)
            logger.info("After generateSpend")
            val queryCashAfter = serviceHub.getCashBalance(USD).toString()
            logger.info("Make donation After CashBalance: $queryCashAfter")
            val cashAfter = serviceHub.vaultService.queryBy<Cash.State>().states
            logger.info("Make donation After CashQuery: $cashAfter")
            val inputStateAndRefs = utx.inputStates().map { serviceHub.toStateAndRef<Cash.State>(it) }
            val outputStates = utx.outputStates().map { it.data as Cash.State }
            val signingKeys = utx.commands().flatMap { it.signers }
            val cashInputState = inputStateAndRefs.map { it.state.data }
            logger.info("inputStateAndRefs: $inputStateAndRefs")
            logger.info("cashInputState: $cashInputState")
            logger.info("cashOutputStates: $outputStates")
            logger.info("cashSigningKeys: $signingKeys")

            subFlow(SendStateAndRefFlow(otherSession, inputStateAndRefs))
            logger.info("Before pledgedCashStates")
            // Send the payload back to the fundraiser.
            val pledgedCashStates = CashStatesPayload(inputStateAndRefs, outputStates, signingKeys)

            logger.info("After pledgedCashStates")
            otherSession.send(pledgedCashStates)
        }

        @Suspendable
        override fun call() {
            val campaignResult = otherSession.receive<CampaignResult>().unwrap { it }

            when (campaignResult) {
                is CampaignResult.Success -> handleSuccess(campaignResult.campaignRef)
                is CampaignResult.Failure -> return logger.info("Failed Campaign")
            }

            val flow = object : SignTransactionFlow(otherSession) {
                override fun checkTransaction(stx: SignedTransaction) = Unit
            }

            subFlow(flow)
        }
    }
}

