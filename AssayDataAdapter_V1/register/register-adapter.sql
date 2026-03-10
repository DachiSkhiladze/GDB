--
-- Test implementations of assay data adapter (used for demo and documentation purposes)
--

-- Generates random plate-based Assay Values
insert into adapter_implementation (ID,LABEL,ACTIVE,SORT_ORDER,DESCRIPTION,JAVA_CLASSNAME, adapter_interface_id) values (
	adapter_implementation_seq.nextval,'Random Plate-Based Assay Results','Y',91,
	'Generates random plate-based Assay Data for test and demonstration purposes',
	'biologics.sc.adapter.assay.RandomPlateAssayDataAdapter',
	(select id from adapter_interface where java_classname = 'genedata.bx.adapter.assay.AssayDataAdapter')
);

-- adapter supports all assays
insert into adapter_implementation_assay (ASSAY_ID,ADAPTER_IMPLEMENTATION_ID) (
	select id, adapter_implementation_seq.currval from assay
);
-- assay_adapter_configuration: SC only
insert into assay_adapter_configuration (id, adapter_implementation_id, for_cld, for_sc) values(assay_adapter_config_seq.nextval, adapter_implementation_seq.currval, 'N', 'Y');


-- Tests the custom input parameters
insert into adapter_implementation (ID,LABEL,ACTIVE,SORT_ORDER,DESCRIPTION,JAVA_CLASSNAME, adapter_interface_id) values(adapter_implementation_seq.nextval,'Test of the Custom Input Parameters','Y',92,'Tests the custom input parameters. Does not generate any Assay Values.','biologics.sc.adapter.assay.TestParameterAssayDataAdapter',(select id from adapter_interface where java_classname = 'genedata.bx.adapter.assay.AssayDataAdapter'));

-- adapter supports all assays
insert into adapter_implementation_assay (ASSAY_ID,ADAPTER_IMPLEMENTATION_ID) (select id, adapter_implementation_seq.currval from assay);

-- assay_adapter_configuration: SC only
insert into assay_adapter_configuration (id, adapter_implementation_id, for_cld, for_sc) values(assay_adapter_config_seq.nextval, adapter_implementation_seq.currval, 'N', 'Y');
