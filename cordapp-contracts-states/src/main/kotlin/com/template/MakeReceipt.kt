package com.template

import co.paralleluniverse.fibers.Suspendable
import com.template.contractAndstate.EndCampaign
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.groupAbstractPartyByWellKnownParty
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import net.corda.core.utilities.unwrap
import net.corda.finance.flows.TwoPartyDealFlow
import java.time.Instant
import java.util.*

object MakeReceipt {
    /** This flow run by fundraiser for creating a receipt state
     * and updating the campaign state*/
    @StartableByRPC
    @InitiatingFlow
    class Initiator(
            private val campaignReference: UniqueIdentifier,
            private val amount: Amount<Currency>
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            //Pick notary
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(campaignReference))
            logger.info("queryCriteria: $queryCriteria")
            val campaignInputStateAndRef = serviceHub.vaultService.queryBy<Campaign>(queryCriteria).states.get(0)
            logger.info(" campaignInputStateAndRef : ${campaignInputStateAndRef.state}")
            val campaignState = campaignInputStateAndRef.state.data
            val donationInputStateAndRef = serviceHub.vaultService.queryBy<Donation>().states.filter { it.state.data.campaignReference == campaignReference }.get(0)
            logger.info("donationInputStateAndRef : $donationInputStateAndRef")
            val donationState = donationInputStateAndRef.state.data

            // Assemble the transaction component
            val acceptReceiptCommand = Command(CampaignContract.Commands.AcceptReceipt(),campaignState.fundraiser.owningKey)
            val createReceiptCommand = Command(ReceiptContract.Commands.Create(), listOf(campaignState.fundraiser.owningKey,campaignState.recipient.owningKey))

            //Output states
            val makeReceiptState = Receipt(campaignReference,amount,campaignState.recipient,campaignState.fundraiser,serviceHub.identityService.requireWellKnownPartyFromAnonymous(donationState.donor),campaignState.bank,campaignState.recipientName)
            val receiptOutputAndContract = StateAndContract(makeReceiptState,ReceiptContract.ID)


            val newTransferAmount = campaignState.transferAmount + amount
            val newRemainingAmount = campaignState.raised - newTransferAmount
            logger.info("remainingAmount: $newRemainingAmount")
            logger.info("amount: $amount")
            donationState.amount
            val campaignOutputState = campaignState.copy(transferAmount = newTransferAmount,remainingAmount = newRemainingAmount)
            val campaignOutputStateAndContract = StateAndContract(campaignOutputState, CampaignContract.ID)
            //Build transaction
            val utx = TransactionBuilder(notary = notary).withItems(
                    receiptOutputAndContract, //Output state
                    campaignOutputStateAndContract, //Output state
                    campaignInputStateAndRef,      //Input
                    createReceiptCommand,        //Command
                    acceptReceiptCommand          //Command

            )
            utx.setTimeWindow(Instant.now(),30.seconds)  // TODO
            //Sign, sync, finalise, and commit transaction
            val ptx = serviceHub.signInitialTransaction(builder = utx,signingPubKeys = listOf(campaignState.fundraiser.owningKey))
            val session = initiateFlow(campaignState.recipient)
            subFlow(IdentitySyncFlow.Send(otherSide = session, tx = ptx.tx))
            logger.info("MakeReceipt ptx: $ptx")
            logger.info("MakeReceipt session: $session")
            //Collect signature
            val stx = subFlow(CollectSignaturesFlow(ptx, setOf(session), setOf(campaignState.fundraiser.owningKey)))
            val ftx = subFlow(FinalityFlow(stx))
            logger.info("MakeReceipt stx: $stx")
            logger.info("MakeReceipt ftx: $ftx")
            return ftx
        }
    }


    /**
     * The responders run by recipient, donor to check the proposed transaction
     * Then sign the transaction
     * */
    @InitiatedBy(MakeReceipt.Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            logger.info("MakeReceipt Responder1")
            subFlow(IdentitySyncFlow.Receive(otherSideSession = otherSession))
            logger.info("othersession: $otherSession")
            val flow: SignTransactionFlow = object :SignTransactionFlow(otherSession){
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) = requireThat {

                }
            }
            val stx: SignedTransaction = subFlow(flow)
            logger.info("MakeReceipt Responder2")
            //wait for transaction has been committed
            return waitForLedgerCommit(stx.id)
        }
    }
}

