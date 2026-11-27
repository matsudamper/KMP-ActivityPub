package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.logic.FollowingService
import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.follower.MovedActorFollowings

internal class MovedActorFollowingService(
    private val followingService: FollowingService,
) : MovedActorFollowings {
    override suspend fun move(movedFromActorId: String, movedTo: RemoteActor) {
        followingService.moveFollowing(movedFromActorUri = movedFromActorId, movedToActorUri = movedTo.actorId)
    }
}
