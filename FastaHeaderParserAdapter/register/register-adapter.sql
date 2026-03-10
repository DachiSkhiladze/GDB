-- -----------------------------------------------------------------------------
-- Sample configuration 
-- -----------------------------------------------------------------------------

insert into adapter_implementation (
    id, label, active, description, java_classname, adapter_interface_id
) values (
    adapter_implementation_seq.nextval,
    'Sample implementation of v2 FastaHeaderParser', 
    'Y',
    'Naming scheme: <plate>|<welladdress>|<quality score>|<chain=L|H>',
    'biologics.adapter.fasta.SamplePlateBasedFastaHeaderParser',
    (select id from adapter_interface where java_classname = 'genedata.bx.adapter.fasta.v2.FastaHeaderParser')
);

