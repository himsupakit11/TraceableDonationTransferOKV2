package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.SchedulableFlow
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.loggerFor
import net.corda.finance.flows.AbstractCashFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import java.util.*

object IssueToken {
    @SchedulableFlow
    @InitiatingFlow
    /** Issue token*/
    class Initiator(private val issuingAmount: Amount<Currency>) : FlowLogic<AbstractCashFlow.Result>() {

        private fun selfCashIssue(amount: Amount<Currency>): FlowLogic<AbstractCashFlow.Result> {
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val issueRef = OpaqueBytes.of(0)
            val issueRequest = CashIssueFlow.IssueRequest(amount, issueRef, notary)
            val flow = CashIssueFlow(issueRequest)
            logger.info("inside Cash issue flow")
            return flow
        }
        @Suspendable
        override fun call(): AbstractCashFlow.Result {
            val logger = loggerFor<IssueToken>()
            val issuingCash = subFlow(selfCashIssue(issuingAmount))
            logger.info("issuingCash: $issuingCash")
            val campaignStateAndRefs = serviceHub.vaultService.queryBy<Campaign>().states.get(0)
            logger.info("campaignStateAndRefs: $campaignStateAndRefs")
            CashPaymentFlow(issuingAmount,campaignStateAndRefs.state.data.donor)
            logger.info("Issuing Cash Successfully")
            return issuingCash

        }
    }
}