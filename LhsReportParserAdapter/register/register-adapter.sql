-- -----------------------------------------------------------------------------
-- Sample configuration 
-- -----------------------------------------------------------------------------

-- Sample adapter implementation: CoTransfect Plate Set via LHS Report
insert into adapter_implementation (
	id, label, active, description, java_classname, adapter_interface_id
) values (
	adapter_implementation_seq.nextval,
	'Co-Transfect via LHS Report (sample)', 'Y',
	'Create Plate Set with Co-Transfected Antibody Clones via LHS Report (sample implementation).',
	'biologics.adapter.lhs.SampleCoTransfectPlateSet',
	(select id from adapter_interface where java_classname = 'genedata.bx.adapter.lhs.v2.LhsCoTransfectPlateSet')
);

insert into plate_management_configuration (id, adapter_implementation_id, for_cld, for_sc) values (
	plate_management_config_seq.nextval, 
	(select id from adapter_implementation 
		where java_classname = 'biologics.adapter.lhs.SampleCoTransfectPlateSet'),
	'N', -- CLD
	'Y'  -- SC
);
