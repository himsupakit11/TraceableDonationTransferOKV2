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

const val CASH_PROGRAM_ID: ContractClassName = "net.corda.finance.contracts.asset.Cash"

// *********
// * Flows *
// *********

object EndCampaign{
    @SchedulableFlow
    @InitiatingFlow
    class Initiator(private val stateRef: StateRef): FlowLogic<SignedTransaction>() {
        /**Request cash payload from donors*/
        @Suspendable
        fun requestDonoatedCash(sessions: List<FlowSession>): CashStatesPayload{
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
//            val campaignReference = Donation.DonationSchema.DonationEntity::campaign_reference.equal(campaign.linearId.id.toString())
//            val customCriteria = QueryCriteria.VaultCustomQueryCriteria(campaignReference)
            val donationStateAndRefs = serviceHub.vaultService.queryBy<Donation>().states //
//            println("campaignReference: $campaignReference")
//            println("customCriteria: $customCriteria")
//            println("donationStateAndRefs: $donationStateAndRefs")
//            val donationStateAndRefs = donationsForCampaign(serviceHub,campaign)
            val campaignInputStateAndRef = serviceHub.toStateAndRef<Campaign>(stateRef)

            //Create commands
            val endCampaignCommand = Command(CampaignContract.Commands.End(), campaign.fundraiser.owningKey)
            val cancelPledgeCommand = Command(DonationContract.Cancel(), campaign.fundraiser.owningKey)

            // Add all components
            donationStateAndRefs.forEach { utx.addInputState(it) } // input
            utx.addInputState(campaignInputStateAndRef) //input
            utx.addCommand(endCampaignCommand) // command
            utx.addCommand(cancelPledgeCommand) //command

            return utx
        }

        @Suspendable
        fun handleSuccess(campaign: Campaign,sessions: List<FlowSession>): TransactionBuilder{
            logger.info("handleSuccess1")
            val utx = cancelDonation(campaign)
            //Collect the cash states from donors
            val cashStates = requestDonoatedCash(sessions)
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
            //Only fundraiser can run this flow
//            if (campaign.fundraiser != ourIdentity){
//                throw FlowException("Only the campaign manager can run this flow.")
//            }
//            logger.info("Before Initiate Flow")
            //Retrieve the donations for each campaign
//            val donationsForCampaign = donationsForCampaign(serviceHub,campaign)
//            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(campaign.linearId))
//            logger.info("linear id: ${campaign.linearId}")
//            logger.info("EndCampagin linear id: $queryCriteria")
//
//
            val donationsForCampaign = serviceHub.vaultService.queryBy<Donation>().states.map { it.state.data }
            logger.info("donationsForCampaign: $donationsForCampaign")
            //Create flow sessions for all donors
            val sessions = donationsForCampaign
                    .map { serviceHub.identityService.requireWellKnownPartyFromAnonymous(it.donor) }
                    .distinct().map { donor ->
                logger.info("donor : $donor")
            initiateFlow(donor)

            }
            //logger.info("sessions: $sessions")
            logger.info("End campaign sessions: $sessions ")
            logger.info("After Initiate Flow")
            // End campaign whatever it success or not
            val utx = when {
                campaign.raised < campaign.target -> {
                    sessions.forEach { session -> session.send(CampaignResult.Failure()) }
                    logger.info("Failed")
                    cancelDonation(campaign)

                }
                else -> handleSuccess(campaign,sessions)

        }
            // Sign, finalise and distribute the transaction.
            logger.info("Before ptx")
            val ptx = serviceHub.signInitialTransaction(utx)
            logger.info("Before stx")
            val stx = subFlow(CollectSignaturesFlow(ptx, sessions.map { it }))
            println("sessionaa:${sessions.map { it }}")
            logger.info("After stx")
            val ftx = subFlow(FinalityFlow(stx))
            logger.info("Success")
            return ftx
    }


}

    @InitiatedBy(Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {
        // Get donation state for this campaign
        @Suspendable
        fun handleSuccess(campaignRef: StateRef) {
            val campaign = serviceHub.loadState(campaignRef).data as Campaign
            logger.info("campaignaa: $campaign ")
            logger.info("otehrSession: $otherSession")

            val results = serviceHub.vaultService.queryBy<Donation>().states.map { it.state.data }

            logger.info("Endcampaign ouridentity: $ourIdentity")
//            val results = donationsForCampaign(serviceHub, campaign)
            logger.info("results: $results")
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

            // We need to send the cash state dependency transactions so the manager can verify the tx proposal.
            subFlow(SendStateAndRefFlow(otherSession, inputStateAndRefs))
            logger.info("Before pledgedCashStates")
            // Send the payload back to the campaign manager.
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
                override fun checkTransaction(stx: SignedTransaction) = Unit // TODO
            }

            subFlow(flow)
        }
    }


/**Pick all of donations for the specified campaigns*/
fun donationsForCampaign(services: ServiceHub,campaign: Campaign): List<StateAndRef<Donation>> {
    val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
    println("generalCriteria: $generalCriteria")
    return builder {
        val campaignReference = Donation.DonationSchema.DonationEntity::campaign_reference.equal(campaign.linearId.id.toString())
        println("campaignReference: $campaignReference")
        val customCriteria = QueryCriteria.VaultCustomQueryCriteria(campaignReference)
        println("customCriteria: $customCriteria")
        val criteria = generalCriteria `and` customCriteria
        println("criteria: $criteria")
        services.vaultService.queryBy<Donation>(criteria)

    }.states
    }

}

