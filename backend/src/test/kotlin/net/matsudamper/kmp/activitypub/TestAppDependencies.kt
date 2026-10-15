package net.matsudamper.kmp.activitypub

@Suppress("FunctionName")
internal fun TestDependencies(
    repositories: FakeRepositories = FakeRepositories(),
    env: ServerEnv = ServerEnv(emptyMap()),
): AppDependencies = AppDependencies(repositories = repositories, env = env)
