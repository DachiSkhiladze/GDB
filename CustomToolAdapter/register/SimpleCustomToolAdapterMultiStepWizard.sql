-- Register adapter
insert into adapter_implementation (id, label, active, description, java_classname, adapter_interface_id
) values (
	adapter_implementation_seq.nextval,
	'Simple Custom Tool (multi-step wizard)', 'Y',
	'Example implementation of the Custom Tool adapter show-casing input parameters defining the number of steps in this wizard.',
	'biologics.adapter.ct.SimpleCustomToolAdapterMultiStepWizard',
	(select id from adapter_interface where java_classname = 'genedata.bx.adapter.ct.CustomToolAdapter')
);

-- Configure adapter for Project Show page and Browse tables
insert into external_tool_configuration (
	id,
	adapter_implementation_id,
	entity_configuration_id,
	sort_order,
	browse_table,
	show_page
) values (
	external_tool_config_seq.nextval,
	adapter_implementation_seq.currval,
	(select id from entity_configuration where entity='Project'),
	1135,
	'Y',
	'Y'
);
