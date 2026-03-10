-- -----------------------------------------------------------------------------
-- Sample configuration 
-- -----------------------------------------------------------------------------

insert into adapter_implementation (id, adapter_interface_id, sort_order, label, description, java_classname)
values (
  adapter_implementation_seq.nextval, 
  (select id from adapter_interface where java_classname = 'genedata.bx.adapter.pp.platewell.PpPlateWellPropertyAdapter'),
  197,
  'Via Well Layout Map with Aliquot barcode information', 
  'Reads a file containing well layout information to assign Aliquot barcodes to existing wells of Protein Production Plates.', 
  'biologics.adapter.pp.platewell.SamplePpPlateWellPropertyAdapter'
);
