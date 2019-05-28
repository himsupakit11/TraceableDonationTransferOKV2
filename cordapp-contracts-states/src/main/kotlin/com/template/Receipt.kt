package com.template

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.loggerFor
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import java.security.PublicKey
import java.util.*
import net.corda.finance.USD
import java.time.Instant
import java.util.logging.Logger
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table
//Contract

class ReceiptContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.template.ReceiptContract"
        val logger = loggerFor<Receipt>()
    }

    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
        class End : TypeOnlyCommandData(), Commands
        class Update : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val receiptCommand = tx.commands.requireSingleCommand<ReceiptContract.Commands>()
        val setOfSigners = receiptCommand.signers.toSet()

        when (receiptCommand.value) {
            is ReceiptContract.Commands.Create -> verifyCreate(tx, setOfSigners)
            is ReceiptContract.Commands.End -> verifyEnd(tx, setOfSigners)
            is ReceiptContract.Commands.Update-> verifyUpdate(tx, setOfSigners)
            else -> throw IllegalArgumentException("")
        }

    }
    private fun verifyUpdate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "There must be campaign state and receipt state when updating a receipt " using (tx.inputStates.size == 2)
        "There must be only the campaign output state when updating a receipt " using (tx.outputStates.size == 1)
    }
    private fun verifyEnd(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        logger.info("Verify Receipt End")
        "Making a receipt must consume the campaign state and the receipt state " using (tx.inputStates.size == 2)
        "There must be only the campaign output state when making a receipt " using (tx.outputStates.size == 1)
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //Group donation by campaign id
        "There must be a campaign input state when making a receipt. " using (tx.inRefsOfType<Campaign>().size == 1)
        val receiptState = tx.groupStates(Receipt::class.java, { it.linearId })
        "Only one receipt can be made at a time" using (receiptState.size == 1)
        val campaignStates= tx.groupStates(Campaign::class.java, { it.linearId })
        "There must be a campaign state when making a receipt" using (campaignStates.isNotEmpty())

        val receiptStatesGroup: LedgerTransaction.InOutGroup<Receipt, UniqueIdentifier> = receiptState.single()
        val receipt: Receipt = receiptStatesGroup.outputs.single()
        val campaignStatesGroup = campaignStates.single()

        val campaign = campaignStatesGroup.outputs.single()
        val campaignInput = campaignStatesGroup.inputs.get(0)

        "Recipient in receipt state must be the same as specified recipient in campaign state" using (receipt.recipientName == campaign.recipientName)
        val remainingAmount = campaignInput.remainingAmount
        logger.info("remainingAmount: $remainingAmount")
        logger.info("receipt amount: ${receipt.amount}")
        "Transfer amount should not be zero" using (receipt.amount > 0.DOLLARS)
        logger.info("Campaign transfer amount: ${campaign.transferAmount}")
        logger.info("Campaign remaining amount: ${campaign.remainingAmount}")
        logger.info("Campaign raised amount: ${campaign.raised}")
        "Transfer amount cannot be more than remaining amount  " using (receipt.amount <= remainingAmount)
        logger.info("Recipient signers: $signers")
        "The campaign must be signed by fundraiser and recipient" using (signers == keysFromParticipants(receipt)-campaign.bank.owningKey-campaign.donor.owningKey)
    }
}

//State
data class Receipt(
        val campaignReference: UniqueIdentifier,
        val amount: Amount<Currency>,
        val recipient: Party,
        val fundraiser: Party,
        val donor: AbstractParty,
        val bank: Party,
        val recipientName: String,
        val timestamp: Instant,
        override val participants: List<AbstractParty> = listOf(recipient,fundraiser,donor,bank),
        override val linearId: UniqueIdentifier = UniqueIdentifier()
): LinearState,QueryableState{
    override fun supportedSchemas() = listOf(ReceiptSchemaV1)
    override fun generateMappedObject(schema: MappedSchema) = ReceiptSchemaV1.ReceiptEntity(this)

    object ReceiptSchemaV1 : MappedSchema(Receipt::class.java,1, listOf(ReceiptEntity::class.java)){
        @Entity
        @Table(name = "receipts")
        class ReceiptEntity(receipt: Receipt): PersistentState() {
            @Column
            var amount: Long = receipt.amount.quantity
            @Column
            @Lob
            var recipient: ByteArray = receipt.recipient.owningKey.encoded
            @Column
            @Lob
            var fundraiser: ByteArray = receipt.fundraiser.owningKey.encoded
            @Column
            @Lob
            var donor: ByteArray = receipt.donor.owningKey.encoded
            @Column
            var campaign_reference: String = receipt.campaignReference.id.toString()
           @Column
            var linear_id: String = receipt.linearId.id.toString()
        }
    }
}
