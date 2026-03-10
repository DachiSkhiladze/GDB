-- Adapter implementations needs to be registered with Biologics

-- You can register only one MaterialPreRegistrationAdapter per material entity type

insert into ADAPTER_IMPLEMENTATION (
	id,
	label,
	description,
	java_classname,
	adapter_interface_id
)
values (
	adapter_implementation_seq.nextval,
	'Example Material Pre-Registration',
	'Example implementation of the pre-registration of Biologics material entities.',
	'biologics.adapter.registration.material.ExampleMaterialPreRegistration',
	(select id
		from ADAPTER_INTERFACE
		where java_classname = 'genedata.bx.adapter.register.material.MaterialPreRegistrationAdapter')
);

update ENTITY_CONFIGURATION
	set material_pre_registration_id =
		(select id from ADAPTER_IMPLEMENTATION
			where java_classname = 'biologics.adapter.registration.material.ExampleMaterialPreRegistration')
	where entity in (
		'CellLineBatch',
		'HostCellLineBatch',
		'ProteinExpressionBatch',
		'ProteinPurificationBatch',
		'VectorBatch'
	);

