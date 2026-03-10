-- -----------------------------------------------------------------------------
-- Sample configuration 
-- -----------------------------------------------------------------------------

-- register ColumnCreateAliquotGroupsAdapter
insert into adapter_implementation (ID,LABEL,ACTIVE,SORT_ORDER,DESCRIPTION,JAVA_CLASSNAME, adapter_interface_id)
  values (
    adapter_implementation_seq.nextval,
    'From Generic Column-based File'
    ,'Y',
    null,
    'Import Aliquot Groups from tab-separated text file',
    'biologics.adapter.aliquotgroup.ColumnCreateAliquotGroupsAdapter',
    (select id from adapter_interface where java_classname = 'genedata.bx.adapter.aliquotgroup.CreateAliquotGroupsAdapter')
);
