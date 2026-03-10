package com.demo.adapter;

import java.util.Map;

import genedata.bx.adapter.NameGenerator;
import genedata.bx.adapter.NumberingFactory;

/**
 * Demo: Automatic Protein Name Generator for Genedata Biologics.
 *
 * When a user creates a new Protein (PPT) in Biologics, this adapter
 * automatically generates a standardized name using the pattern:
 *
 *     {PROJECT}-AB-{sequential number}
 *
 * For example: "ProjectAlpha-AB-1", "ProjectAlpha-AB-2", etc.
 *
 * HOW IT WORKS:
 *   1. Biologics calls setConfiguration() — we read the project prefix
 *      from the PARAMETER table in the database.
 *   2. When a user creates a protein, Biologics calls generateName() —
 *      we use NumberingFactory to get the next sequential number and
 *      build the name.
 *   3. showNameToUserForEdit() returns true — the user sees the generated
 *      name and can modify it before saving.
 *
 * ADAPTER LIFECYCLE:
 *   setConfiguration(config)  -->  generateName(context, numbering)
 *         ^                               |
 *   reads config from DB           returns "ProjectAlpha-AB-42"
 */
public class ProjectPrefixNameGenerator implements NameGenerator {

    private static final long serialVersionUID = 1L;

    /** Default prefix if none is configured in the database */
    private static final String DEFAULT_PREFIX = "DEMO";

    /** The project prefix, loaded from database PARAMETER table */
    private String projectPrefix = DEFAULT_PREFIX;

    /**
     * Called by Biologics on startup. Receives key-value pairs from the
     * PARAMETER table. We look for our custom config key to set the prefix.
     */
    @Override
    public void setConfiguration(Map<String, String> configuration) {
        String configuredPrefix = configuration.get(
            "com_demo_adapter_ProjectPrefixNameGenerator_prefix"
        );
        if (configuredPrefix != null && !configuredPrefix.trim().isEmpty()) {
            this.projectPrefix = configuredPrefix.trim();
        }
    }

    /**
     * Called by Biologics each time a user creates a new Protein.
     *
     * @param context    contains entity info like ALIAS, RELATED_ENTITY_ALIAS
     * @param numbering  factory that provides sequential numbers (persisted
     *                   in the NAME_GENERATION_NUMBERING table)
     * @return           the generated name, e.g. "ProjectAlpha-AB-7"
     */
    @Override
    public String generateName(Map<String, String> context, NumberingFactory numbering) {
        // Each unique namespace gets its own independent counter.
        // Using our class name + prefix ensures no collisions.
        String namespace = getClass().getName() + "_" + projectPrefix;

        // NumberingFactory.nextNumber() atomically increments and returns
        // the next integer for this namespace (stored in Oracle).
        int nextNumber = numbering.nextNumber(namespace);

        return projectPrefix + "-AB-" + nextNumber;
    }

    /**
     * Optional: pre-fill the name field before the user submits.
     * We return null because our name is generated on submit, not before.
     */
    @Override
    public String prefillName(Map<String, String> context) {
        return null;
    }

    /**
     * If true, the user sees the generated name and can edit it.
     * If false, the name is applied silently.
     */
    @Override
    public boolean showNameToUserForEdit() {
        return true;
    }
}
