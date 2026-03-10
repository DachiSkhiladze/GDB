-- Register EntityEventListenerAdapter
merge into adapter_implementation a using (
	select 'biologics.adapter.entityeventlistener.BasicEntityEventLogger' as java_classname from dual
) b on (a.java_classname = b.java_classname)
when matched then
	update set active = 'Y'
when not matched then
	insert (id, label, active, description, java_classname, adapter_interface_id) values (
		adapter_implementation_seq.nextval,
		'Basic Entity Event Logger (example)',
		'Y',
		'Example Entity Event Listener adapter for logging all events.',
		b.java_classname,
		(select id from adapter_interface where java_classname = 'genedata.bx.adapter.entityeventlistener.EntityEventListenerAdapter')
	)
;

-- Register the adapter for all currently available entity configurations
insert into adapter_implementation_entity_configuration ( 
	entity_configuration_id,
	entity_event_listener_adapter_id
) select
	ec.id,
	(select id from adapter_implementation where java_classname = 'biologics.adapter.entityeventlistener.BasicEntityEventLogger')
from
	entity_configuration ec
where
	(select category from table_metadata where entity_configuration_id = ec.id) = 'E'
	and not exists (select 1 from adapter_implementation_entity_configuration
		where entity_configuration_id = ec.id
		and entity_event_listener_adapter_id = (select id from adapter_implementation where java_classname = 'biologics.adapter.entityeventlistener.BasicEntityEventLogger'))
;

-- enable logging of all entity attributes
merge into parameter a using (
	select 'biologics_adapter_entityeventlistener_BasicEntityEventLogger_report_entity_attributes' as key from dual
) b on (a.key = b.key)
when matched then
	update set value = 'true'
when not matched then 
	insert (id, key, value, description)
		values (parameter.nextval, b.key, 'true', 'When true the BasicEntityEventLogger reports all entity attributes using the Export Entities WS.')
;
