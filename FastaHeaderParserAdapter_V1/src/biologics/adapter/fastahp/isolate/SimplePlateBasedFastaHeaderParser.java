package biologics.adapter.fastahp.isolate;

import genedata.bx.adapter.IsolateBasedFastaHeaderParser;
import genedata.bx.adapter.assay.Isolate;
import genedata.bx.adapter.assay.IsolateInformationProvider;
import genedata.bx.adapter.plate.Plate;
import biologics.adapter.fastahp.AbstractFastaHeaderParser;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * SimplePlateBasedFastaHeaderParser implementation for testing purposes. It expects   
 * the fasta header line to contain only two fields separated by semicolon:
 * {@code <plateIndex>;<wellAddress>}.  
 * 
 * The {@code plateIndex} is an index to the plates from the selected 
 * Plate Set. This allows to load the sequence file to any project and Plate 
 * Set without the need to rename to the actual matching Plate Names. 
 * Indexing the plates starts with one.
 * 
 * The {@code wellAddress} is the address of the well: A1 or A01 up to H12.
 * For rows and columns counting starts at 1. 
 * 
 * <div style="font-size:x-small">
 * Copyright 2010-2011 Genedata AG. All Rights Reserved.
 * </div>
 */
public class SimplePlateBasedFastaHeaderParser extends AbstractFastaHeaderParser implements IsolateBasedFastaHeaderParser, Serializable {
	private static final long serialVersionUID = 1L;
	
	private static Logger log = Logger.getLogger(SimplePlateBasedFastaHeaderParser.class);
	
	private int wellRow;
	private int wellColumn;
	private Plate plate;
	private List<Plate> plates;
	
	@Override
	public boolean handlesPlateBasedIsolates() {
		return true; // this parser supports plate-based parsing
	}
	
	@Override
	public boolean handlesPlateFreeIsolates() {
		return false; // plate-free parsing is not supported
	}
	
	@Override
	public Plate getIdentifiedPlate() {
		return this.plate;
	}
	
	@Override
	public int getIdentifiedWellColumn() {
		return this.wellColumn;
	}
	
	@Override
	public int getIdentifiedWellRow() {
		return this.wellRow;
	}
	
	@Override
	public Isolate getIdentifiedIsolate() {
		return null; // not required for this plate-based example
	}
	
	@Override
	public void setPlates(List<Plate> plates) {
		if (null == plates) {
			throw new IllegalArgumentException("No Plates to identify given.");
		}
		this.plates = plates;
	}
	
	@Override
	public void setIsolateInformationProvider(IsolateInformationProvider provider) {
		// not required for this plate-based example
	}
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// no custom settings available for this implementation 
	}
	
	@Override
	public void parse(String headerLine) {
		
		log.debug("Header line: " + headerLine);
		reset();
		
		if (null == headerLine || 0 == headerLine.length()) {
			return;
		}
		if (headerLine.startsWith(">")) {
			headerLine = headerLine.substring(1);
		}
		
		// expected format: plateIndex;wellAddress
		// e.g. 1;A1
		String[] fields = headerLine.split(";");
		if (2 != fields.length) {
			throw new IllegalArgumentException("The header line '" + headerLine +
				"' does not have the expected format and cannot be recognized.");
		}
		
		identifyPlate( fields[0].trim() );
		parseWellAddress( fields[1].trim() );
		
		log.debug("Plate Name = '" + this.plate + "' WellRow = " + this.wellRow +
			" WellColumn = " + this.wellColumn);
	}
	
	@Override
	protected void reset() {
		this.chainInfo = null;
		this.plate = null;
		this.wellRow = 0;
		this.wellColumn = 0;
	}
	
	private void identifyPlate(String plateIndex) {
		int idx = Integer.parseInt(plateIndex)-1;
		this.plate = (idx >= 0 && idx < plates.size()) ?  plates.get(idx) : null;
	}
	
	private void parseWellAddress(String wellAddress) {
		// NOTE: limited to single letter (up to 384 well plate)
		this.wellRow = wellAddress.charAt(0)-'A'+1;
		this.wellColumn = Integer.parseInt(wellAddress.substring(1));
	}
}
