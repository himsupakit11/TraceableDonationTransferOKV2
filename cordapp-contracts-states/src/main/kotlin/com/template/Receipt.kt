package com.template

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table
//Contract

class ReceiptContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.template.ReceiptContract"
    }

    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
    }

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val receiptCommand = tx.commands.requireSingleCommand<ReceiptContract.Commands>()
        val setOfSigners = receiptCommand.signers.toSet()

        when (receiptCommand.value) {
            is ReceiptContract.Commands.Create -> verifyCreate(tx, setOfSigners)
            else -> throw IllegalArgumentException("")
        }

    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //Group donation by campaign id
        "There must be a donation input state when making a receipt. " using (tx.inRefsOfType<Campaign>().size == 1)
        val receiptState = tx.groupStates(Receipt::class.java, { it.linearId })
        "Only one receipt can be made at a time" using (receiptState.size == 1)
        val campaignStates= tx.groupStates(Campaign::class.java, { it.linearId })
        "There must be a campaign state when making a receipt" using (campaignStates.isNotEmpty())

        val receiptStatesGroup: LedgerTransaction.InOutGroup<Receipt, UniqueIdentifier> = receiptState.single()
//        "No input states should be consumed when making a receipt" using (receiptStatesGroup.outputs.size == 1)
        val receipt: Receipt = receiptStatesGroup.outputs.single()
        val campaignStatesGroup = campaignStates.single()
        val campaign = campaignStatesGroup.outputs.single()
//          "Transfer amount cannot be more than remaining amount  " using (receipt.amount > campaign.remainingAmount)
        // "The campaign must be signed by fundraiser and recipient" using (signers == keysFromParticipants(receipt))
    }
}

//State
data class Receipt(
        val campaignReference: UniqueIdentifier,
        val amount: Amount<Currency>,
        val recipient: Party,
        val fundraiser: Party,
        val donor: AbstractParty,
        override val participants: List<AbstractParty> = listOf(recipient,fundraiser,donor),
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
