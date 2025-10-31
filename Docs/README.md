# Docs Service

This module exposes the gRPC-based documentation service that stores categories and markdown documents in Elasticsearch.

## Prerequisites

* Java 21
* Docker (for running the supporting Elasticsearch cluster)

To launch the infrastructure locally you can rely on the repository level `docker-compose.yml`:

```bash
docker compose up elasticsearch kibana
```

## Running the Tests

The Docs module currently ships unit tests for the domain layer. They can be executed without any external services:

```bash
./gradlew :Docs:test
```

From the project root you can also run the full build, which will execute the same test task as part of the lifecycle:

```bash
./gradlew :Docs:build
```

> ℹ️  The Gradle wrapper downloads dependencies from Maven Central the first time you run these commands. Make sure your network settings allow outbound HTTPS traffic.

## Manual Verification via gRPC

1. Start Elasticsearch with Docker as shown above.
2. Launch the Docs service:
   ```bash
   ./gradlew :Docs:bootRun
   ```
3. (Optional but recommended) Start the Envoy gateway so requests flow through the external authorization filter:
   ```bash
   docker compose up envoy
   ```
4. Use your preferred gRPC client (e.g. `grpcurl`) against the generated stubs in `Docs/src/main/proto` to create categories and documents, then run `SearchDocuments` to verify indexing behaviour. When calling through Envoy make sure the target is `localhost:9090` and include a valid `Authorization: Bearer <jwt>` header issued by the Auth service.

### Sending gRPC requests from Postman

Postman can call the Docs service directly without any additional gateway. After the
service is running:

1. Open Postman and click **New → gRPC Request**.
2. In the **Server URL** field enter the Envoy listener address, for example `localhost:9090`. If you bypass Envoy for development you can target the Docs gRPC port directly (`localhost:5054`).
3. Click **Select method** and import the protobuf definition from `Docs/src/main/proto/docs.proto`. Postman will parse the file and list all RPCs under the `DocsService` service.
4. Choose an RPC (for instance `CreateCategory`) from the methods list. Postman shows the request message schema on the right.
5. Open the **Metadata** tab and add an entry with key `authorization` and value `Bearer <jwt-token-from-auth>`. This ensures Envoy allows the call and the Docs service accepts it.
6. Compose the request payload in JSON format that mirrors the proto message. Example body for creating a root category:

   ```json
   {
     "name": "Getting Started"
   }
   ```

   To create a document under that category, switch to the `CreateDocumentation` method and send:

   ```json
   {
     "title": "Install Guide",
     "content": "# Installation\n...",
     "categoryId": "<category-id-from-previous-response>"
   }
   ```

7. Use `ListCategoryContents` to view folders/files and `SearchDocuments` to verify Elasticsearch indexing. Postman displays the responses in JSON, making it easy to follow the folder/file workflow.

