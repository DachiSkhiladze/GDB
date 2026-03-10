
insert into adapter_implementation(id, label, description, java_classname, adapter_interface_id) values (
        adapter_implementation_seq.nextval,
        'Cys in CDR',
        'Annotate Cysteines that can be found within a CDR',
        'biologics.adapter.annotation.CysteineInCdrAnnotationAdapter',
        (select id from adapter_interface where JAVA_CLASSNAME like '%.annotation.SequenceAnnotationAdapter')
);

insert into adapter_implementation_agenda (
    agenda_id,
    adapter_implementation_index,
    adapter_implementation_id
) values (
    (select id from agenda where entry_key = 'sr'),
    161,
    adapter_implementation_seq.currval 
-- (select id from adapter_implementation where java_classname like '%.CysteineInCdrAnnotationAdapter')
);


insert into parameter (ID, KEY, VALUE, DESCRIPTION) values(parameter_seq.nextval, 'biologics.adapter.annotation.CysteineInCdrAnnotationAdapter.feature_type_name', 'misc_feature', '');
insert into parameter (ID, KEY, VALUE, DESCRIPTION) values(parameter_seq.nextval, 'biologics.adapter.annotation.CysteineInCdrAnnotationAdapter.feature_name', 'Free C', '');
insert into parameter (ID, KEY, VALUE, DESCRIPTION) values(parameter_seq.nextval, 'biologics.adapter.annotation.CysteineInCdrAnnotationAdapter.feature_description', 'Free Cysteine in CDR', '');

