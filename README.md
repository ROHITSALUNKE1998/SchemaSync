# SchemaSync

SchemaSync compares the observed shape of a MongoDB collection export with an Elasticsearch index mapping. The first milestone is a read-only Java 21 / Spring Boot CLI for reviewing conflicts before indexing. All examples are original synthetic catalog records.

## Run the sample

From this directory with JDK 21 and Maven installed:

```sh
mvn package
java -jar target/schemasync-0.1.0.jar fixtures/catalog.jsonl fixtures/mapping.json
```

The first argument is a UTF-8 MongoDB export containing one JSON document per line. The second is the JSON response from a single index's `GET /catalog/_mapping`. The report goes to standard output. A completed audit exits successfully even when it finds conflicts. Invalid input fails the run.

The [sample report](examples/report.json) covers three documents. One stores quantity as the string "7" and another as the number 12. The index expects a long. Validate strings and their range before conversion. The additional field aisle has no declared mapping.

## Docker demo

With Docker Compose and Linux containers available, run:

```sh
docker compose run --build --rm audit
docker compose down -v
```

The demo starts MongoDB and Elasticsearch on a private Compose network with no published ports. It imports the synthetic catalog by SKU, exports the collection, initializes the catalog index, fetches its mapping and passes both files to the CLI. The cleanup command removes demo containers and volumes. The index creation body is in `fixtures/index.json`.

## Report semantics and limits

- Paths and observed types are sorted for repeatable reviews. Nested objects use dotted paths. Arrays contribute their element types. Nulls and empty arrays are recorded but do not establish a concrete type.
- Findings distinguish mixed source types, an unmapped field, a strict mapping conflict, and an unsupported target type. Elasticsearch may coerce some values that this audit flags for review.
- Supported targets are keyword, text, long, double, boolean, object and nested. Numeric checks cover signed 64-bit integer bounds for long. The CLI audits observed fields only and skips MongoDB's root _id.
- Use plain JSON values in this milestone. Extended JSON wrappers, literal dotted keys and dollar-prefixed keys are rejected. The audit does not infer original BSON widths, evaluate dynamic templates, or model every index setting.
- The normalization suggestion for strings targeting long is advisory. Data transformation and writes are outside this milestone.

## Next iterations

1. Add a normalization preview for integer strings with tests for whitespace, overflow, fractions and rejected records.
2. Generate a reviewable Elasticsearch ingest pipeline from accepted rules. Test it against synthetic data using the pipeline simulation API.
3. Add a dashboard for comparing saved audit reports after the report format stabilizes.
