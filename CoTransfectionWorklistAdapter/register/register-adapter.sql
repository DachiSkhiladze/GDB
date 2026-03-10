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
        'with Co-Transfection Group Number (TSV including Source Antibody Clone ID and Name)',
        'Co-Transfection Worklist Sample',
        '',
        'biologics.adapter.lhs.controlfile.SampleCoTransfectionWorklistAdapter',
        ( select id
                from ADAPTER_INTERFACE
                where java_classname = 'genedata.bx.adapter.plate.controlfile.v2.CoTransfectionWorklistAdapter')
);

