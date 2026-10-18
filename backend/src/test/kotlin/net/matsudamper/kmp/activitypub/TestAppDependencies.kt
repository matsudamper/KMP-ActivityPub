package net.matsudamper.kmp.activitypub

@Suppress("FunctionName")
internal fun TestDependencies(
    repositories: FakeRepositories = FakeRepositories(),
    env: ServerEnv = ServerEnv(emptyMap()),
    remoteActors: FakeRemoteActors = FakeRemoteActors(),
    activityDelivery: FakeActivityDelivery = FakeActivityDelivery(),
): AppDependencies = AppDependencies(
    repositories = repositories,
    sessionStore = FakeUserSessionStore(),
    loginFailureStore = FakeLoginFailureStore(),
    remoteActors = remoteActors,
    activityDelivery = activityDelivery,
    env = env,
)
