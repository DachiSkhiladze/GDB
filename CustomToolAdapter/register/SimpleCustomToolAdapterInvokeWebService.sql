-- Register adapter
insert into adapter_implementation (id, label, active, description, java_classname, adapter_interface_id
) values (
	adapter_implementation_seq.nextval,
	'Simple Custom Tool (invoke sequence web service)', 'Y',
	'Example implementation of the Custom Tool adapter which invokes the sequence web service for selected PPTs and prints them on screen.',
	'biologics.adapter.ct.SimpleCustomToolAdapterInvokeWebService',
	(select id from adapter_interface where java_classname = 'genedata.bx.adapter.ct.CustomToolAdapter')
);

-- Configure for TPPs (known internally as 'Ppt')
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
	(select id from entity_configuration where entity='Ppt'),
	1134,
	'Y',
	'Y'
);
