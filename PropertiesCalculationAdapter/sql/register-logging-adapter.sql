--
-- SQL statements for the registration of the Properties Calculation Adapter
--

-- Inactivate existing implementations of the Properties Calculation Adapter
update adapter_implementation set active = 'N' where adapter_interface_id in
                                                     (select id from adapter_interface where java_classname like '%.PhysChemPropertiesCalculation');

-- Register the 'Empty Implementation With Logging' Adapter
insert into adapter_implementation (ID,LABEL,ACTIVE,DESCRIPTION,JAVA_CLASSNAME,ADAPTER_INTERFACE_ID) values (
  adapter_implementation_seq.nextval,
  'Empty Implementation With Logging',
  'Y',
  'Empty adapter implementation for the calculation of physico-chemical properties with logging.',
  'biologics.adapter.propertiescalculation.EmptyPhysChemPropertiesWithLogging',
  (select id from adapter_interface where java_classname like '%.PhysChemPropertiesCalculation'));

-- end of file
