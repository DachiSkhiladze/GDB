-- Register adapter
insert into adapter_implementation (ID,LABEL,ACTIVE,SORT_ORDER,DESCRIPTION,JAVA_CLASSNAME,adapter_interface_id)
values(
	adapter_implementation_seq.nextval,
	'Custom Tool with single step wizard',
	'Y',
	null,
	null,
	'biologics.adapter.ct.TableAndDynamicFeaturesDemonstratingCustomToolAdapter',
	(select id from adapter_interface where java_classname = 'genedata.bx.adapter.ct.CustomToolAdapter')
);

-- Configure adapter for Project Show page and Browse tables
insert into external_tool_configuration
(id,ADAPTER_IMPLEMENTATION_ID,ENTITY_CONFIGURATION_ID,SORT_ORDER,BROWSE_TABLE,SHOW_PAGE) values (
  external_tool_config_seq.nextval,
  (select id from adapter_implementation where label = 'Custom Tool with single step wizard'),
  (select id from entity_configuration where entity = 'Project'),
  1003,
  'Y',
  'Y'
);