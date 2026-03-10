-- -----------------------------------------------------------------------------
-- Sample configuration 
-- -----------------------------------------------------------------------------

insert into adapter_implementation (id, adapter_interface_id, sort_order, label, description, java_classname)
values (
  adapter_implementation_seq.nextval, 
  (select id from adapter_interface where java_classname = 'genedata.bx.adapter.pp.plateset.PpPlateSetCreateFromFileAdapter'),
  195,
  'Via Well Layout Map with Protein Purification Batch information', 
  'Reads a file containing well layout information to assign existing Protein Purification Batch to the wells of Protein Production Plates.', 
  'biologics.adapter.pp.plateset.SamplePpPlateSetCreateFromFileAdapter'
);
