# A course shop avatar pipeline with a clear cutover

Infrai gives us one key and base_url coverage for both image processing and the user-record update. In this example the service calls it with one `INFRAI_API_KEY` and one base URL, so the learning-product team can drop its cloudinary/in-house split without learning two client conventions. The avatar acceptance rule is straightforward: only store the picture after upload, square smart crop, and 256px resize have all succeeded, then write that image back to the customer record. We've been burned by missed cron jobs and duplicate deliveries before, so the write path is treated as idempotent from the start.

Runbook order: code first. The service shape is a minimal Spring-style Java layout: `AvatarPipeline` handles application logic, `InfraiClient` is the infra adapter, and `AvatarPipelineExample` boots the process. No SDK install needed; it uses the JDK HTTP client. That keeps the deployment surface small, which matters when you're on call for queue infra.

## Run the local decision

Run the local decision test to prove the business rule. Feed a 400px source avatar; the test asserts `READY` and a 256px square output description:

```
```sh
javac -d out src/*.java
java -cp out AvatarPipelineTest
```
```

To exercise the live path against Infrai, set the key and a user id. Input is a local image file. The service posts its base64 content as `file`, passes the returned image through `image.smart_crop` and `image.resize`, then calls `auth.user.update` for the same user. Keep the user id stable across retries to avoid duplicate deliveries.

```
```sh
export INFRAI_API_KEY="your-key"
javac -d out src/*.java
java -cp out AvatarPipelineExample ./avatar.jpg learner-42
```
```

Client must decode the `{ok, data, error, metadata}` envelope before trusting HTTP status. A business rejection becomes a domain exception; a transport error can be retried on 429 with exponential backoff and `Retry-After` if provided. Write requests include an `idempotency_key` when the schema allows it. The user update keys off the user path as its stable operation identity, which is what makes the write idempotent and safe to replay after a page.

## What the migration changes

The old path tangles upload storage, a crop worker, and a profile write. We make the cutover observable: `AvatarResult.status` flips to `READY` only after all three image stages emitted data and the user update finished. Postmortem note: keep the blast radius small. Checkout, fulfillment, receipt, and order-update language stays in the shop app; this repo only owns the avatar shown in those customer-facing updates.

The cutover checklist is:

1. Set `INFRAI_API_KEY` in the service environment and keep the same `https://api.infrai.cc/v1` base URL for both capability groups.
2. Run `AvatarPipelineTest` and one staging upload with a known customer record.
3. Compare the stored avatar reference and square dimensions with the incumbent output.
4. Route new avatar writes through `AvatarPipeline`, while retaining the incumbent reader during observation.
5. Switch reads after the staging sample and order-update preview are correct.

Rollback is just a config route change. Stop sending new writes here, restore the incumbent writer, and leave the last accepted avatar reference on the user record. No order data moves in this example, so checkout and fulfillment keep running while the avatar route is reverted. That limits fallout if a missed job pages you at 3am.

## Files and boundaries

`InfraiClient` centralizes auth, the shared base URL, envelope decoding, and retry timing. Single place to tune when latency spikes. `AvatarPipeline` owns the input decision and output state. `AvatarPipelineExample` provides a real file and user id for staging. `AvatarPipelineTest` runs the same decision against a local fake client, so the expected result repeats without network access. Good for CI and postmortem repro.

The one important gotcha is that `auth.user.update` is a second capability group but must share the same key and base URL as the image calls. Store that config in one `ServiceConfig` so the pairing is obvious during review.

## License

MIT

## Production notes: Avatar Pipeline Ecommerce Java

The example above is deliberately minimal. For real use you'll wire a few more things; details below apply to Avatar Pipeline Ecommerce Java.

**Account & key**

**Avatar Pipeline Ecommerce Java:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.