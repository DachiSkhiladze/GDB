package biologics.adapter.fastahp.isolate;

import genedata.bx.adapter.IsolateBasedFastaHeaderParser;
import genedata.bx.adapter.assay.Isolate;
import genedata.bx.adapter.assay.IsolateInformationProvider;
import genedata.bx.adapter.plate.Plate;
import biologics.adapter.fastahp.AbstractFastaHeaderParser;

import java.io.Serializable;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * SimplePlateFreeFastaHeaderParser implementation for testing purposes. It expects
 * the fasta header line to contain two fields separated by an underline:
 * {@code <isolateName>_<chainInfo>}.
 * 
 * The {@code isolateName} is the name or the qualified identifier (e.g. "CL-3") of
 * the Antibody Clone.
 * 
 * The {@code chainInfo} is the optional chain information.
 * 
 * <div style="font-size:x-small">
 * Copyright 2010-2011 Genedata AG. All Rights Reserved.
 * </div>
 */
public class SimplePlateFreeFastaHeaderParser extends AbstractFastaHeaderParser implements IsolateBasedFastaHeaderParser, Serializable {
	private static final long serialVersionUID = 1L;
	
	private static Logger log = Logger.getLogger(SimplePlateFreeFastaHeaderParser.class);
	
	private Isolate isolate;
	private IsolateInformationProvider isolateInformationProvider;
	
	
	@Override
	public boolean handlesPlateBasedIsolates() {
		return false; // plate-based parsing is not supported
	}
	
	@Override
	public boolean handlesPlateFreeIsolates() {
		return true;// this parser supports plate-free parsing
	}
	
	@Override
	public Plate getIdentifiedPlate() {
		return null; // not required for this plate-free example
	}
	
	@Override
	public int getIdentifiedWellColumn() {
		return 0; // not required for this plate-free example
	}
	
	@Override
	public int getIdentifiedWellRow() {
		return 0; // not required for this plate-free example
	}
	
	@Override
	public Isolate getIdentifiedIsolate() {
		return this.isolate;
	}
	
	@Override
	public void setPlates(List<Plate> plates) {
		// not required for this plate-free example
	}
	
	@Override
	public void setIsolateInformationProvider(IsolateInformationProvider provider) {
		this.isolateInformationProvider = provider;
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
		
		recognizeIsolate(headerLine);
		recognizePrimer(headerLine);
		
		log.debug("Antibody Clone name: " +
				((null != isolate) ? isolate.getAlias() : ""));
		
		log.debug("Chain information: " +
				((null != chainInfo) ? chainInfo : ""));
	}
	
	/**
	 * Retrieves the Isolate by name or qualified identifier (e.g. 'CL-3').
	 * It is assumed that the Isolate name is in the first (whitespace-separated)
	 * part of the header line. It may be postfixed by the primer information
	 * (separated by an underscore).
	 * <p>
	 * Example header lines:
	 * <ul>
	 * <li>WXYZ-006-H08-19-1_BioB</li>
	 * <li>CL-3_VLup.ab1   1502     28    814  ABI</li>
	 * </ul>
	 * @param headerLine The header line.
	 */
	protected void recognizeIsolate(String headerLine) {
		
		String[] items = headerLine.split("\\s+");
		String firstItem = items[0];
		
		int pos = firstItem.lastIndexOf('_');
		
		String isolateName;
		if (pos > 0) {
			isolateName = firstItem.substring(0, pos);
		}
		else {
			isolateName = firstItem;
		}
		
		this.isolate = isolateInformationProvider.retrieveIsolate(isolateName);
	}

	@Override
	protected void reset() {
		this.chainInfo = null;
		this.isolate = null;
	}
}
