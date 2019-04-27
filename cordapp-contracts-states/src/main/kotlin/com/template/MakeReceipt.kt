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
    /** Create a new donation state for updating the existing campaign state*/
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
            val donationInputStateAndRef = serviceHub.vaultService.queryBy<Donation>().states.get(0)
            logger.info("donationInputStateAndRef : $donationInputStateAndRef")
            val donationState = donationInputStateAndRef.state.data


            // Assemble the transaction component
            val acceptReceiptCommand = Command(CampaignContract.Commands.AcceptReceipt(),campaignState.fundraiser.owningKey)
            val createReceiptCommand = Command(ReceiptContract.Commands.Create(), listOf(campaignState.fundraiser.owningKey,campaignState.recipient.owningKey))

            //Output states

            val makeReceiptState = Receipt(campaignReference,amount,campaignState.recipient,campaignState.fundraiser,serviceHub.identityService.requireWellKnownPartyFromAnonymous(donationState.donor))
            val receiptOutputAndContract = StateAndContract(makeReceiptState,ReceiptContract.ID)


            val remainingAmount = campaignState.raised - amount
            logger.info("remainingAmount: $remainingAmount")
            logger.info("amount: $amount")
            val campaignOutputState = campaignState.copy(remainingAmount = remainingAmount,transferAmount = amount)
            val campaignOutputStateAndContract = StateAndContract(campaignOutputState, CampaignContract.ID)
            //Build transaction
            val utx = TransactionBuilder(notary = notary).withItems(
                    receiptOutputAndContract, //Output state
                    campaignOutputStateAndContract,
                    campaignInputStateAndRef,      //Input
                    createReceiptCommand,        //Command
                    acceptReceiptCommand          //Command

            )
            utx.setTimeWindow(Instant.now(),30.seconds)  // TODO
            //Sign, sync, finalise, and commit transaction
            val ptx = serviceHub.signInitialTransaction(builder = utx,signingPubKeys = listOf(campaignState.fundraiser.owningKey))
            val session = initiateFlow(campaignState.recipient)
            subFlow(IdentitySyncFlow.Send(otherSide = session, tx = ptx.tx))
            println("MakeReceipt ptx: $ptx")
            println("MakeReceipt session: $session")
            //Collect signature
            val stx = subFlow(CollectSignaturesFlow(ptx, setOf(session), setOf(campaignState.fundraiser.owningKey)))
            val ftx = subFlow(FinalityFlow(stx))
            println("MakeReceipt stx: $stx")
            println("MakeReceipt ftx: $ftx")
            return ftx
        }
    }


    /**
     * The responders run by recipient, donor to check the proposed transaction

     * */
    @InitiatedBy(MakeReceipt.Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            println("MakeReceipt Responder1")
            subFlow(IdentitySyncFlow.Receive(otherSideSession = otherSession))
            println("othersession: $otherSession")
            val flow: SignTransactionFlow = object :SignTransactionFlow(otherSession){
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) = requireThat {

                }
            }

            val stx: SignedTransaction = subFlow(flow)
            println("MakeReceipt Responder2")
            //wait for transaction has been committed
            return waitForLedgerCommit(stx.id)
        }
    }
}

///** Fundraiser transfer fund to recipient */
//object MakeReceipt {
//    /** Create a new donation state for updating the existing campaign state*/
//    @StartableByRPC
//    @InitiatingFlow
//    class Initiator(
//            private val campaignReference: UniqueIdentifier,
//            private val amount: Amount<Currency>
//    ) : FlowLogic<SignedTransaction>() {
//
//        @Suspendable
//        override fun call(): SignedTransaction {
//            //Pick notary
//            val notary = serviceHub.networkMapCache.notaryIdentities.first()
//            /**Query campaign for fundraiser key, recipient key*/
//            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(campaignReference))
//            val campaignInputStateAndRef = serviceHub.vaultService.queryBy<Campaign>(queryCriteria).states.get(0)
//            val campaignState = campaignInputStateAndRef.state.data
////            val test1 = serviceHub.vaultService.queryBy<Donation>().states
////            logger.info("test1: $test1")
////
////            val test = serviceHub.vaultService.queryBy<Donation>().states.distinct()
////            logger.info("test: $test")
//            val donationInputStateAndRef = serviceHub.vaultService.queryBy<Donation>().states.distinct().get(0)
//            val donationState = donationInputStateAndRef.state.data
////            logger.info("donationInputStateAndRef:$donationInputStateAndRef")
////            logger.info("donationState :$donationState")
//
//
//            // Assemble the transaction component
//            logger.info("1:")
//            val acceptReceiptCommand = Command(CampaignContract.Commands.AcceptReceipt(),campaignState.fundraiser.owningKey)
//            val createReceiptCommand = Command(ReceiptContract.Commands.Create(), listOf(campaignState.fundraiser.owningKey,campaignState.recipient.owningKey))
//            logger.info("2:")
//            //Output states
//
//            val makeReceiptState = Receipt(campaignReference,amount,campaignState.recipient,campaignState.fundraiser,donationState.donor)
//            val receiptOutputAndContract = StateAndContract(makeReceiptState,ReceiptContract.ID)
//            logger.info("3:")
//            val remainingAmount = campaignState.raised - amount
//            logger.info("4")
//            val campaignOutputState = campaignState.copy(remainingAmount = remainingAmount,transferAmount = amount)
//            val campaignOutputStateAndContract = StateAndContract(campaignOutputState, CampaignContract.ID)
//            logger.info("5")
//            //Build transaction
//            val utx = TransactionBuilder(notary = notary).withItems(
//                    receiptOutputAndContract, //Output state
//                    //campaignOutputStateAndContract, //Output state
//                    donationInputStateAndRef,      //Input
//                    createReceiptCommand,        //Command
//                    acceptReceiptCommand          //Command
//
//            )
//            utx.setTimeWindow(Instant.now(),30.seconds)  // TODO
//            //Sign, sync, finalise, and commit transaction
//            val ptx = serviceHub.signInitialTransaction(builder = utx,signingPubKeys = listOf(campaignState.fundraiser.owningKey))
//            val session = initiateFlow(campaignState.recipient)
//            subFlow(IdentitySyncFlow.Send(otherSide = session, tx = ptx.tx))
//            println("MakeReceipt ptx: $ptx")
//            println("MakeReceipt session: $session")
//            //Collect signature
//            val stx = subFlow(CollectSignaturesFlow(ptx, setOf(session), setOf(campaignState.fundraiser.owningKey)))
//            val ftx = subFlow(FinalityFlow(stx))
//            println("MakeReceipt stx: $stx")
//            println("MakeReceipt ftx: $ftx")
//            return ftx
//        }
//    }
//
//
//    /**
//     * The responders run by recipient, donor to check the proposed transaction
//
//     * */
//    @InitiatedBy(MakeReceipt.Initiator::class)
//    class Responder(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
//        @Suspendable
//        override fun call(): SignedTransaction {
//            println("MakeReceipt Responder1")
//            subFlow(IdentitySyncFlow.Receive(otherSideSession = otherSession))
//            println("othersession: $otherSession")
//            val flow: SignTransactionFlow = object :SignTransactionFlow(otherSession){
//                @Suspendable
//                override fun checkTransaction(stx: SignedTransaction) = requireThat {
//
//                }
//            }
//
//            val stx: SignedTransaction = subFlow(flow)
//            println("MakeReceipt Responder2")
//            //wait for transaction has been committed
//            return waitForLedgerCommit(stx.id)
//        }
//    }
//}
