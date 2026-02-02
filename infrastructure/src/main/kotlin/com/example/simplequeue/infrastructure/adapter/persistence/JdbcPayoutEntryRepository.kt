package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.port.PayoutEntryRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JdbcPayoutEntryRepository(
    private val jdbcClient: JdbcClient,
) : PayoutEntryRepository {

    override fun link(payoutId: UUID, entryId: UUID) {
        jdbcClient
            .sql("INSERT INTO payout_entries (payout_id, entry_id) VALUES (?, ?) ON CONFLICT DO NOTHING")
            .param(payoutId)
            .param(entryId)
            .update()
    }

    override fun findEntriesByPayoutId(payoutId: UUID): List<UUID> =
        jdbcClient
            .sql("SELECT entry_id FROM payout_entries WHERE payout_id = ?")
            .param(payoutId)
            .query { rs, _ -> rs.getObject("entry_id", UUID::class.java) }
            .list()

    override fun findPayoutByEntryId(entryId: UUID): UUID? =
        jdbcClient
            .sql("SELECT payout_id FROM payout_entries WHERE entry_id = ?")
            .param(entryId)
            .query { rs, _ -> rs.getObject("payout_id", UUID::class.java) }
            .optional()
            .orElse(null)
}
