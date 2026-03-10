-- -----------------------------------------------------------------------------
-- Sample configuration 
-- -----------------------------------------------------------------------------

-- register SampleCoTransfectionWorklistAdapter
insert into ADAPTER_IMPLEMENTATION (
        id,
        label,
        description,
        entry_key,
        java_classname,
        adapter_interface_id
)
values (
        adapter_implementation_seq.nextval,
        'Generic (using Well Address and Plate Name / Barcode) (Example Implementation) (version 2)',
        'Generic LHS control file using Well Address and Barcode (API V2)',
        '',
        'biologics.adapter.lhs.controlfile.SampleCreateLhsControlFileAdapter',
        ( select id
                from ADAPTER_INTERFACE
                where java_classname = 'genedata.bx.adapter.plate.controlfile.v2.LhsCreateControlFile')
);

insert into plate_management_configuration (id, adapter_implementation_id, for_cld, for_sc) values (
    plate_management_config_seq.nextval,
    (select id from adapter_implementation
        where java_classname = 'biologics.adapter.lhs.controlfile.SampleCreateLhsControlFileAdapter'),
    'N', -- CLD -- not yet supported
    'Y'  -- SC
);

