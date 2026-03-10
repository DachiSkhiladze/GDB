-- -----------------------------------------------------------------------------
-- Sample configuration 
-- -----------------------------------------------------------------------------

-- register PlateAssayDataAdapter
insert into adapter_implementation (ID,LABEL,ACTIVE,SORT_ORDER,DESCRIPTION,JAVA_CLASSNAME, adapter_interface_id)
  values (
    adapter_implementation_seq.nextval,
    'Plate-based data (sample implementation)'
    ,'Y',
    95,
    'Import Assay Data from tab-separated text files',
    'biologics.adapter.assay.PlateAssayDataAdapter',
    (select id from adapter_interface where java_classname = 'genedata.bx.adapter.assay.v2.AssayDataAdapter')
);
-- adapter supports all assays
insert into adapter_implementation_assay (ASSAY_ID,ADAPTER_IMPLEMENTATION_ID) (
	select id, adapter_implementation_seq.currval from assay
);
-- assay_adapter_configuration: SC only
insert into assay_adapter_configuration (id, adapter_implementation_id, for_cld, for_sc) values(assay_adapter_config_seq.nextval, adapter_implementation_seq.currval, 'N', 'Y');

-- -----------------------------------------------------------------------------

-- register PlateMultipleAntigenAssayDataAdapter
insert into adapter_implementation (ID,LABEL,ACTIVE,SORT_ORDER,DESCRIPTION,JAVA_CLASSNAME, adapter_interface_id)
  values (
    adapter_implementation_seq.nextval,
    'Plate-based data, multiple antigens (sample implementation)'
    ,'Y',
    90,
    'Import plate-based data with multiple antigens per plate',
    'biologics.adapter.assay.PlateMultipleAntigenAssayDataAdapter',
    (select id from adapter_interface where java_classname = 'genedata.bx.adapter.assay.v2.AssayDataAdapter')
);
-- adapter supports assays that require an antigen
insert into adapter_implementation_assay (ASSAY_ID,ADAPTER_IMPLEMENTATION_ID) (
	(select id, 
    (select id from adapter_implementation 
      where java_classname = 'biologics.adapter.assay.PlateMultipleAntigenAssayDataAdapter'
    )
    from assay where REQUIRE_ANTIGEN = 'Y'
  )
);
-- assay_adapter_configuration: SC only
insert into assay_adapter_configuration (id, adapter_implementation_id, for_cld, for_sc) values(assay_adapter_config_seq.nextval, adapter_implementation_seq.currval, 'N', 'Y');

-- -----------------------------------------------------------------------------

-- register ColumnAssayDataAdapter
insert into adapter_implementation (ID,LABEL,ACTIVE,SORT_ORDER,DESCRIPTION,JAVA_CLASSNAME, adapter_interface_id)
  values (
    adapter_implementation_seq.nextval,
    'Column-based data (sample Implementation) (Version 2)'
    ,'Y',
    95,
    'Import Assay Data from tab-separated text files',
    'biologics.adapter.assay.ColumnAssayDataAdapter',
    (select id from adapter_interface where java_classname = 'genedata.bx.adapter.assay.v2.AssayDataAdapter')
);

-- adapter supports all assays
insert into adapter_implementation_assay (ASSAY_ID,ADAPTER_IMPLEMENTATION_ID) (
	select id, adapter_implementation_seq.currval from assay
);
-- assay_adapter_configuration: CLD and SC  
insert into assay_adapter_configuration (id, adapter_implementation_id, for_cld, for_sc) values(assay_adapter_config_seq.nextval, adapter_implementation_seq.currval, 'Y', 'Y'); 

-- -----------------------------------------------------------------------------

-- register CustomInputParameterExample
insert into adapter_implementation (ID,LABEL,ACTIVE,SORT_ORDER,DESCRIPTION,JAVA_CLASSNAME, adapter_interface_id)
  values (
    adapter_implementation_seq.nextval,
    'Custom Input Parameter Example'
    ,'Y',
    95,
    'Prompt for custom input parameters',
    'biologics.adapter.assay.CustomInputParameterExample',
    (select id from adapter_interface where java_classname = 'genedata.bx.adapter.assay.v2.AssayDataAdapter')
);

-- adapter supports all assays
insert into adapter_implementation_assay (ASSAY_ID,ADAPTER_IMPLEMENTATION_ID) (
	select id, adapter_implementation_seq.currval from assay
);
-- assay_adapter_configuration: CLD and SC  
insert into assay_adapter_configuration (id, adapter_implementation_id, for_cld, for_sc) values(assay_adapter_config_seq.nextval, adapter_implementation_seq.currval, 'Y', 'Y'); 

