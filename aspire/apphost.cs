#:package Aspire.Hosting.AppHost@17.0.0
#:package Aspire.Hosting.Dotnet@17.0.0

using YamlDotNet.Core.Tokens;

var builder = DistributedApplication.CreateBuilder(
    new DistributedApplicationOptions
    {
        Args = args,
        DashboardApplicationName = "JLib Inspector Aspire"
    });

var server = builder.AddDockerfile("server", "..", "docker/Dockerfile.backend")
    .WithHttpEndpoint(targetPort: 8080)
    .WithHttpHealthCheck("/health")
    .WithEnvironment("JDK_JAVA_OPTIONS", "-Xms512m -Xmx1g");

builder.AddDockerfile("frontend", "..", "docker/Dockerfile.frontend")
    .WithHttpEndpoint(targetPort: 3000)
    .WithHttpEndpoint(port: 3001, targetPort: 3001, name: "websocket")
    .WithEnvironment("PORT", "3000")
    .WithEnvironment("JLIB_SERVER_URL", server.GetEndpoint("http"))
    .WithReference(server.GetEndpoint("http"))
    .WaitFor(server);

builder.AddDockerfile("sample-spring-app", "..", "docker/Dockerfile.samplespring")
    .WithArgs("java", "-javaagent:agent.jar=server:8080,otel", "-jar", "sample-spring-app.jar")
    .WithOtlpExporter(OtlpProtocol.HttpJson)
    .WithEnvironment("JDK_JAVA_OPTIONS", "-Xms512m -Xmx1g")
    .WithEnvironment("JLIB_SERVER_URL", server.GetEndpoint("http"))
    .WithReference(server.GetEndpoint("http"))
    .WaitFor(server);

builder.Build().Run();