package biologics.sc.adapter.assay;

import genedata.bx.adapter.ParameterRegistry;
import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.assay.AssayAttribute;
import genedata.bx.adapter.assay.AssayDataAdapter;
import genedata.bx.adapter.assay.AssayValue;
import genedata.bx.adapter.assay.AssayValueFactory;
import genedata.bx.adapter.assay.Isolate;
import genedata.bx.adapter.assay.IsolateInformationProvider;
import genedata.bx.adapter.assay.MissingValueIndicator;
import genedata.bx.adapter.plate.Plate;

import java.io.InputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates random plate-based Assay Values for test and demonstration purposes.
 * 
 * <div style="font-size:x-small">
 * Copyright 2010-2012 Genedata AG. All Rights Reserved.
 * </div>
 * 
 * @deprecated use V2 AssayData API instead
 */
@Deprecated
public class RandomPlateAssayDataAdapter implements AssayDataAdapter, Serializable {
	private static final long serialVersionUID = 1L;

	/** 
	 * The well role which indicates a value well (as opposed to a reserved or control 
	 * well). 
	 */
	private static final String WELL_ROLE_VALUE = "VALUE";
	
	private Reporter reporter;
	private IsolateInformationProvider isolateInformationProvider;
	private List<Plate> plates;
	private int numberOfRows;
	private int numberOfColumns;
	
	private final List<String> commentLines = new ArrayList<String>();
	
	/** The random number generator. */
	private final Random random = new Random();
	
	public void setConfiguration(Map<String, String> configuration) {
		// not required for this example
	}
	
	public void setReporter(Reporter reporter) {
		this.reporter = reporter;
	}
	
	public void setParameterRegistry(ParameterRegistry parameterRegistry) {
		// not required for this example
	}
	
	public void setIsolateInformationProvider(
			IsolateInformationProvider isolateInformationProvider) {
		this.isolateInformationProvider = isolateInformationProvider;
	}
	
	public void setMissingValueIndicators(
			List<MissingValueIndicator> missingValueIndicators) {
		// not required for this example
	}
	
	public boolean requiresInputStream() {
		return false;
	}
	
	public void setInputStream(InputStream inputStream) {
		// not required for this example
	}
	
	public boolean requiresPlateSet() {
		return true;
	}
	
	public void setPlates(List<Plate> plates) {
		this.plates = plates;
	}
	
	public void setPlateDimensions(int numberOfRows, int numberOfColumns) {
		this.numberOfRows = numberOfRows;
		this.numberOfColumns = numberOfColumns;
	}
	
	public List<AssayValue> perform(
			AssayValueFactory assayValueFactory, 
			List<AssayAttribute> assayAttributes) {
		
		List<AssayValue> assayValues = new ArrayList<AssayValue>();
		
		if ((plates == null) || (plates.size() == 0)) {
			reporter.error("Failed to generate the Assay Data: The required plates are not available.");
			return assayValues;
		}
		
		try {
			int skippedWellsCounter = 0;
			for (Plate plate : plates) {
				for (int rowIndex = 1; rowIndex <= numberOfRows; rowIndex++) {
					for (int columnIndex = 1; columnIndex <= numberOfColumns; columnIndex++) {
						if (!WELL_ROLE_VALUE.equals( plate.getRole(rowIndex, columnIndex) )) {
							skippedWellsCounter++;
							continue;
						}
						
						Long identifier = plate.getWell(rowIndex, columnIndex);
						
						if (identifier == null) {
							skippedWellsCounter++;
							continue;
						}
						
						assayValues.addAll(
							generateRandomAssayValues(assayValueFactory, assayAttributes, identifier) );
					}
				}
			}
			
			if (skippedWellsCounter > 0) {
				reporter.info("Number of reserved, control or empty wells: " + skippedWellsCounter);
			}
			
			reporter.info("Generated random values for " +
					plates.size() + " plate" + (plates.size() > 1 ? "s" : "") +
					" with " + numberOfRows + " rows, " + numberOfColumns + " columns.");
		}
		catch (Exception e) {
			reporter.error("Failed to generate random Assay Values: " + e.getMessage() );
		}
		
		return assayValues;
	}

	public List<String> getCommentLines() {
		return commentLines;
	}

	public void initialize() {
		// not required for this example
	}

	public void setParameterValues(Map<String, String> parameterValueMap) {
		// not required for this example
	}

	/**
	 * Generates random assay values for all assay attributes of the given assay.
	 * 
	 * @param assayValueFactory The factory which generates new Assay Values.
	 * @param assayAttributes The list of assay attributes.
	 * @param identifier The identifier number of the antibody clone.
	 * 
	 * @return The list of generated random assay values.
	 */
	private List<AssayValue> generateRandomAssayValues(
			AssayValueFactory assayValueFactory, 
			List<AssayAttribute> assayAttributes, 
			Long identifier)
	{
		List<AssayValue> assayValues = new ArrayList<AssayValue>();
		
		Isolate isolate = isolateInformationProvider.retrieveIsolate(identifier);
		
		if (isolate == null) {
			reporter.warn("Antibody Clone not available for identifier: " + identifier);
			return assayValues;
		}
		
		for (AssayAttribute assayAttribute : assayAttributes) {
			try {
				Double randomValue = Double.valueOf( getRandomValue() );
				
				AssayValue assayValue = assayValueFactory.createAssayValue(isolate, assayAttribute, randomValue);
				
				assayValues.add(assayValue);
			}
			catch (Exception e) {
				reporter.error("Failed to generate the random assay value: " + e.getMessage() );
			}
		}
		
		return assayValues;
	}

	/**
	 * @return The generated random value.
	 */
	private double getRandomValue()
	{
		double d = random.nextDouble() * 10.0;
		
		BigDecimal bd = new BigDecimal(d);
		bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP);
		
		return bd.doubleValue();
	}
}
