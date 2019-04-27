package com.template

import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey
import java.security.Timestamp
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table

class DonationContract: Contract {
    companion object {
        @JvmStatic
        val ID = "com.template.DonationContract"
    }

    interface Commands : CommandData
    class Create : TypeOnlyCommandData(), Commands
    class Cancel : TypeOnlyCommandData(), Commands
    class AcceptReceipt: TypeOnlyCommandData(), Commands
    override fun verify(tx: LedgerTransaction) {
        val donationCommand: CommandWithParties<Commands> = tx.commands.requireSingleCommand()
        val setOfSigners: Set<PublicKey> = donationCommand.signers.toSet()

        when (donationCommand.value) {
            is Create -> verifyCreate(tx, setOfSigners)
            is Cancel -> verifyCancel(tx, setOfSigners)
            is AcceptReceipt ->verifyReceipt(tx,setOfSigners)
            else -> throw IllegalArgumentException("Command not found")
        }
    }
    private fun verifyReceipt(tx: LedgerTransaction,signers: Set<PublicKey>) = requireThat {
        println("verifyReceipt")
        "Making a receipt must be only one input state" using (tx.inputStates.size == 1)
//        "Receipt must be signed by fundraiser" using (signers == keysFromParticipants())
//        "Only one input state must be produced when making a receipt" using (tx.outputStates.size == 1)
//        val campaignOutput = tx.outputsOfType<Campaign>().single()
//        val receiptOutput = tx.outputsOfType<Receipt>().single()
//        "The raised amount must be equal" using (campaignOutput.raised == receiptOutput.amount)
    }
    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //Group donation by campaign id
        val donationState: List<LedgerTransaction.InOutGroup<Donation, UniqueIdentifier>> = tx.groupStates(Donation::class.java, { it.linearId })
        "Only one donation can be made at a time" using (donationState.size == 1)
        val campaignStates: List<LedgerTransaction.InOutGroup<Campaign, UniqueIdentifier>> = tx.groupStates(Campaign::class.java, { it.linearId })
        "There must be a campaign state when making a donation" using (campaignStates.isNotEmpty())

        val donationStatesGroup: LedgerTransaction.InOutGroup<Donation, UniqueIdentifier> = donationState.single()
        "No input states should be consumed when making a donation" using (donationStatesGroup.outputs.size == 1)
        val donation: Donation = donationStatesGroup.outputs.single()

        "Donation amount cannot be zero amount" using (donation.amount > Amount(0, donation.amount.token))

        "The campaign must be signed by donor nad manager" using (signers == keysFromParticipants(donation))

    }

    private fun verifyCancel(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        val donationGroups = tx.groupStates(Donation::class.java, { it.linearId })
        "There must be a campaign input state when the donation is cancelled. " using (tx.inRefsOfType<Campaign>().size == 1)
        donationGroups.forEach { (inputs, outputs) ->
            "There must be no output states produced when the donation is cancelled. " using (outputs.isEmpty())
            "There must be no duplicate donation states" using (inputs.size == 1)
            val campaign = tx.inputsOfType<Campaign>().single()
            val donation = inputs.single()
            "You are cancelling a donation for another campaign." using (donation.campaignReference == campaign.linearId)
            "The cancel donation must be singed by fundraiser" using (campaign.fundraiser.owningKey == signers.single())
        }
    }
}

data class Donation(
    val campaignReference: UniqueIdentifier,
    val fundraiser: Party,
    val donor: AbstractParty,
    val amount: Amount<Currency>,
    val timestamp: Instant,
    val paymentMethod: String,
    override val participants: List<AbstractParty> = listOf(donor,fundraiser),
    override val linearId: UniqueIdentifier = UniqueIdentifier()
): LinearState,QueryableState{
    override fun supportedSchemas() = listOf(DonationSchema)
    override fun generateMappedObject(schema: MappedSchema) = DonationSchema.DonationEntity(this)

    object DonationSchema : MappedSchema(Donation::class.java,1, listOf(DonationEntity::class.java)){
    @Entity
    @Table(name = "donations")
    class DonationEntity(donation: Donation): PersistentState() {
        @Column
        var currency: String = donation.amount.token.toString()
        @Column
        var amount: Long = donation.amount.quantity
        @Column
        @Lob
        var donor: ByteArray = donation.donor.owningKey.encoded
        @Column
        @Lob
        var fundraiser: ByteArray = donation.fundraiser.owningKey.encoded
        @Column
        var campaign_reference: String = donation.campaignReference.id.toString()
        @Column
        var linear_id: String = donation.linearId.id.toString()
        }
    }
}
