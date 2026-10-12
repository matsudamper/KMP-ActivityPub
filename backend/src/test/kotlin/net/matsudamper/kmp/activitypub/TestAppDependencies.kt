package net.matsudamper.kmp.activitypub

internal fun testDependencies(
    repositories: FakeRepositories = FakeRepositories(),
    env: ServerEnv = ServerEnv(emptyMap()),
): AppDependencies = AppDependencies(repositories = repositories, env = env)
