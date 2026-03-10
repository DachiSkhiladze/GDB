-- Register adapter
insert into adapter_implementation (id, label, active, description, java_classname, adapter_interface_id
) values (
	adapter_implementation_seq.nextval,
	'Simple Custom Tool (redirect immediately example)', 'Y',
	'Example implementation of the Custom Tool adapter which redirects immediately to an external URI.',
	'biologics.adapter.ct.SimpleCustomToolAdapterRedirectImmediately',
	(select id from adapter_interface where java_classname = 'genedata.bx.adapter.ct.CustomToolAdapter')
);

-- Configure adapter for Project show page and browse tables
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
	1131,
	'Y',
	'Y'
);

-- Set up base URL for the URL the adapter eventually redirects to
insert into parameter (
	id,
	biologics_user_id,
	key,
	value,
	user_specific,
	description
) values (
	parameter_seq.nextval,
	null,
	'simple_custom_tool_adapter_redirect_base_url',
	'http://gdbadmin/showRequestParameter.jsp',
	'N',
	'The base URL to redirect to when the SimpleCustomToolAdapterRedirectImmediately is invoked.'
);
