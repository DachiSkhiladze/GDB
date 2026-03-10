-- ============================================================================
-- Register the Demo Name Generator adapter with Genedata Biologics
-- Run this SQL as the Biologics schema owner against Oracle
-- ============================================================================

-- Step 1: Register the adapter implementation
INSERT INTO adapter_implementation (
    id,
    label,
    description,
    java_classname,
    adapter_interface_id
) VALUES (
    adapter_implementation_seq.nextval,
    'Demo Project Prefix Name Generator',
    'Generates protein names using pattern: {PROJECT}-AB-{number}',
    'com.demo.adapter.ProjectPrefixNameGenerator',
    (SELECT id FROM adapter_interface
     WHERE java_classname = 'genedata.bx.adapter.NameGenerator')
);

-- Step 2: Assign it to the Protein (Ppt) entity type
UPDATE entity_configuration
SET name_generator_adapter_id = (
    SELECT id FROM adapter_implementation
    WHERE java_classname = 'com.demo.adapter.ProjectPrefixNameGenerator'
)
WHERE entity = 'Ppt';

-- Step 3 (Optional): Configure the project prefix
-- If omitted, the adapter defaults to "DEMO"
INSERT INTO parameter (id, key, value, description) VALUES (
    parameter_seq.nextval,
    'com_demo_adapter_ProjectPrefixNameGenerator_prefix',
    'ProjectAlpha',
    'Prefix used by the Demo Name Generator for protein names'
);

COMMIT;
