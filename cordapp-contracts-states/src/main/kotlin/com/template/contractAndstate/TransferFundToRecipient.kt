package com.template.contractAndstate

import co.paralleluniverse.fibers.Suspendable
import com.template.*
import net.corda.core.contracts.Amount.Companion.sumOrZero
import net.corda.core.contracts.Command
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.finance.USD
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.getCashBalance

object TransferFundToRecipient {
    @SchedulableFlow
    @InitiatingFlow
    class Initiator(private val stateRef: StateRef) : FlowLogic<SignedTransaction>() {

        /**Request cash payload from fundraiser*/
        @Suspendable
        fun requestDonoatedCash(sessions: List<FlowSession>): CashStatesPayload{
            logger.info("requestDonoatedCash.Session: $sessions")
            val cashStates = sessions.map { session ->
                //Sent Success message to all donors
                session.send(CampaignResult.Success(stateRef))
                //Resolve the transaction
                subFlow(ReceiveStateAndRefFlow<ContractState>(session))
                //Receive the cash inputs, outputs and keys
                session.receive<CashStatesPayload>().unwrap { it }
            }
            logger.info("requestDonoatedCash cashStates: $cashStates")
            return CashStatesPayload(
                    cashStates.flatMap { it.inputs },
                    cashStates.flatMap { it.outputs },
                    cashStates.flatMap { it.signingKeys }
            )
        }
        private fun cancelDonation(campaign: Campaign): TransactionBuilder {
            // Pick a notary
            val notary: Party = serviceHub.networkMapCache.notaryIdentities.first()
            val utx = TransactionBuilder(notary = notary)

            val receiptStateAndRefs = serviceHub.vaultService.queryBy<Receipt>().states.filter { it.state.data.campaignReference == campaign.linearId }

            logger.info("receiptStateAndRefs: $receiptStateAndRefs")
            val campaignInputStateAndRef = serviceHub.toStateAndRef<Campaign>(stateRef)
            val campaignState = campaignInputStateAndRef.state.data
            logger.info("campaignInputStateAndRef: $campaignInputStateAndRef")
            logger.info("cancelDonation1")
            //Create commands
            val endCampaignCommand = Command(CampaignContract.Commands.End(), campaign.fundraiser.owningKey)
            val endReceiptCommand = Command(ReceiptContract.Commands.End(), listOf(campaign.fundraiser.owningKey,campaign.recipient.owningKey))

            //Output State
            val campaignOutputState = campaignState.copy(status = "Out Of Date")
            val campaignOutputStateAndContract = TransactionState(campaignOutputState,CampaignContract.ID,notary,null)
            logger.info("cancelDonation2")
            // Add all components
            receiptStateAndRefs.forEach { utx.addInputState(it) } // input
            utx.addInputState(campaignInputStateAndRef) //input
            utx.addOutputState(campaignOutputStateAndContract) // output
            utx.addCommand(endCampaignCommand) // command
            utx.addCommand(endReceiptCommand) //command
            logger.info("cancelDonation3")
            return utx
        }

        @Suspendable
        fun handleSuccess(campaign: Campaign, sessions: List<FlowSession>): TransactionBuilder {
            logger.info("Transfer fund handleSuccess1")
            val utx = cancelDonation(campaign)
            //Collect the cash states from donors
            logger.info("Before cashStates")
            val cashStates = requestDonoatedCash(sessions) /**dead hereeeeeeeeeeeee */
            logger.info("After cashStates")
            //Add the cash inputs, outputs and commands
            cashStates.inputs.forEach { utx.addInputState(it) }
            cashStates.outputs.forEach { utx.addOutputState(it, CASH_PROGRAM_ID) }
            utx.addCommand(Cash.Commands.Move(),cashStates.signingKeys)
            logger.info("Transfer fund handleSuccess2")
            return utx
        }

        @Suspendable
        override fun call(): SignedTransaction {
            //Get campaign data
            val campaign = serviceHub.loadState(stateRef).data as Campaign
            logger.info("Transfer fund campaign : $campaign")
            logger.info("*******************************")
            logger.info("Transfer fund ourIdentity: $ourIdentity")
            logger.info("Transfer fund fundraiser: ${campaign.fundraiser}")
            logger.info("================================")
            val receiptsForCampaign = serviceHub.vaultService.queryBy<Receipt>().states.filter { it.state.data.campaignReference == campaign.linearId }.map { it.state.data }
            logger.info("receiptsForCampaign: $receiptsForCampaign")
            //Only fundraiser can run this flow
            if (campaign.fundraiser != ourIdentity){
                throw FlowException("(Transfer Fund)lOnly the fundraiser can run this flow.")
            }
            //Create flow sessions for all fundraiser
            val sessions = receiptsForCampaign
                    .map { it.fundraiser }
                    .distinct().map { fundraiser ->
                        initiateFlow(fundraiser)

                    }
            logger.info("sessions.fundraiser : $sessions")
            //logger.info("sessions: $sessions")
            logger.info("Transfer fund sessions: $sessions ")
            logger.info("Transfer fund After Initiate Flow")
            // End campaign whatever it success or not
            val utx = handleSuccess(campaign,sessions)


            // Sign, finalise and distribute the transaction.
            logger.info("Transfer fund Before ptx")
            val ptx = serviceHub.signInitialTransaction(utx)
            logger.info("Transfer fund stx: $ptx")
            logger.info("Transfer fund Before stx")
            val stx = subFlow(CollectSignaturesFlow(ptx, sessions.map { it }))
            logger.info("Transfer fund stx: $stx")
            logger.info("Transfer fund session:${sessions.map { it }}")
            logger.info("Transfer fund After stx")
            val ftx = subFlow(FinalityFlow(stx))
            logger.info("Transfer fund Success")
            return ftx
        }


    }

    @InitiatedBy(TransferFundToRecipient.Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        fun handleSuccess(campaignRef: StateRef) {
            logger.info("TransferFund responder")
            val campaign = serviceHub.loadState(campaignRef).data as Campaign
            logger.info("TransferFund Responder campaign: $campaign ")
            logger.info("TransferFund Responder otherSession: $otherSession")
            val results = serviceHub.vaultService.queryBy<Receipt>().states.filter { it.state.data.campaignReference == campaign.linearId }.map { it.state.data }

            logger.info("TransferFund Responder ouridentity: $ourIdentity")
//            val results = donationsForCampaign(serviceHub, campaign)
            logger.info("Responder results: $results")
            val token = results.first().amount.token
            val amount = results.map { it.amount }.sumOrZero(token)
            logger.info("TransferFund Responder Transfer amount: $amount")
            logger.info("TransferFund Responder After amount")
            val queryCash = serviceHub.getCashBalance(USD).toString()
            logger.info("TransferFund Responder CashBalance: $queryCash")
            val cash = serviceHub.vaultService.queryBy<Cash.State>().states
            logger.info("TransferFund Responder CashQuery: $cash")
            logger.info("TransferFund Responder ouridentity: $ourIdentity")
            val (utx, _) = Cash.generateSpend(serviceHub, TransactionBuilder(), amount, ourIdentityAndCert,campaign.recipient)
            logger.info("After generateSpend")
            val queryCashAfter = serviceHub.getCashBalance(USD).toString()
            logger.info("TransferFund Responder After CashBalance: $queryCashAfter")
            val cashAfter = serviceHub.vaultService.queryBy<Cash.State>().states
            logger.info("TransferFund Responder After CashQuery: $cashAfter")
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
//            val campaignResult = otherSession.receive<CampaignResult>().unwrap { it }
//
//            when (campaignResult) {
//                is CampaignResult.Success -> handleSuccess(campaignResult.campaignRef)
//                is CampaignResult.Failure -> return logger.info("Failed Campaign")
//            }

            val flow = object : SignTransactionFlow(otherSession) {
                override fun checkTransaction(stx: SignedTransaction) = Unit // TODO
            }

            subFlow(flow)
        }
    }

}

