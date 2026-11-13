package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kotpub.nodeinfo.NodeInfoUsage
import net.matsudamper.kotpub.nodeinfo.NodeInfoUsageSource
import net.matsudamper.kotpub.nodeinfo.NodeInfoUsers

internal class RepositoryNodeInfoUsage(
    private val accountRepository: AccountRepository,
    private val noteRepository: NoteRepository,
) : NodeInfoUsageSource {
    override suspend fun usage(): NodeInfoUsage =
        NodeInfoUsage(
            users = NodeInfoUsers(total = accountRepository.countAll()),
            localPosts = noteRepository.countAll(),
        )
}
