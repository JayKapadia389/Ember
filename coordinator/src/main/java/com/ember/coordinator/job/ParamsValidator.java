package com.ember.coordinator.job;

import com.ember.coordinator.job.exception.InvalidParamsException;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Checks job params against the model's JSON Schema (decision 037). Schemas are
 * loaded at startup, so a missing or broken schema fails the boot, not a request.
 */
@Component
class ParamsValidator {

    private static final String SCHEMA_BASE = "https://ember.dev/schemas/params/";

    private final JsonMapper jsonMapper;
    private final Map<ModelCatalog, Schema> schemas = new EnumMap<>(ModelCatalog.class);

    ParamsValidator(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemaIdResolvers(resolvers ->
                        resolvers.mapPrefix(SCHEMA_BASE, "classpath:schemas/params/")));
        for (ModelCatalog model : ModelCatalog.values()) {
            Schema schema = registry.getSchema(SchemaLocation.of(SCHEMA_BASE + model.getId() + ".json"));
            schema.initializeValidators();
            schemas.put(model, schema);
        }
    }

    void validate(ModelCatalog model, Map<String, Object> params) {
        List<Error> errors = schemas.get(model).validate(jsonMapper.valueToTree(params));
        if (!errors.isEmpty()) {
            throw new InvalidParamsException(errors.stream().map(Error::toString).toList());
        }
    }
}
