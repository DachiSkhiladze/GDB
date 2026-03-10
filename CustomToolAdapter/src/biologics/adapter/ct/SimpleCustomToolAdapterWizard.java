package biologics.adapter.ct;

import genedata.bx.adapter.ct.CustomToolAdapter;
import genedata.bx.adapter.ct.CustomToolCallback;
import genedata.bx.adapter.ct.CustomToolOptions;
import genedata.bx.adapter.ct.ui.SectionDefinition;
import genedata.bx.adapter.ct.ui.StepDefinition;
import genedata.bx.adapter.ct.ui.component.InputDateComponent;
import genedata.bx.adapter.ct.ui.component.InputDateTimeComponent;
import genedata.bx.adapter.ct.ui.component.InputFileComponent;
import genedata.bx.adapter.ct.ui.component.InputPasswordComponent;
import genedata.bx.adapter.ct.ui.component.InputTextBoxComponent;
import genedata.bx.adapter.ct.ui.component.InputTextMultiSelectionComponent;
import genedata.bx.adapter.ct.ui.wizard.OnFinish;
import genedata.bx.adapter.ct.ui.wizard.OnStart;
import genedata.bx.adapter.ct.ui.wizard.StepTransition;
import genedata.bx.adapter.ct.ui.wizard.Wizard;

import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Simple custom tool adapter exemplifying a 2-step wizard, uploading a file on the
 * first page and downloading it on the second.
 *
 * <div style="font-size:x-small">
 * Copyright 2021 Genedata AG. All Rights Reserved.
 * </div>
 */
public class SimpleCustomToolAdapterWizard implements CustomToolAdapter {
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// nothing to do 
	}
	
	@Override
	public void options(CustomToolOptions options) {
		// nothing to do 
	}
	
	@Override
	public void perform(CustomToolCallback biologics) {
		biologics.viaWizard(new SimpleWizard());
	}
	
	static class SimpleWizard implements Wizard {
		private InputTextBoxComponent inputText, inputName, inputFilename;
		private InputDateComponent inputDate;
		private InputDateTimeComponent inputTime;
		private InputPasswordComponent inputPssw;
		private InputFileComponent inputFile;
		private InputTextMultiSelectionComponent inputPick;
		
		@Override
		public boolean onStart(OnStart onStart) {
			final StepDefinition step = onStart
					.setOptionalNumberOfTotalSteps(2)
					.defineNextStep("Custom Tool File Upload", false)
					.setDescription("Description for 'Custom Tool File Upload'");
			
			SectionDefinition section = step.addSection("Enter details");
			inputText = section.textBoxComponent("Input Field");
			inputDate = section.dateComponent("Date");
			inputTime = section.dateTimeComponent("Date with Time", ZonedDateTime.now());
			inputPick = section.singleSelectionListComponent("Experience", "Profi", "Amateur", "Newbie");
			
			section = step.addSection("Upload File");
			inputName = section.textBoxComponent("Name");
			inputPssw = section.passwordComponent("Password");
						section.readOnlyComponent("", "");	// to get a little bit of space
			inputFile = section.fileUploadComponent("Import");
			
			return true;
		}

		@Override
		public boolean onNext(StepTransition stepTransition) {
			if (null == inputFile.getFile()) {
				stepTransition.getContext().getReporter().error("Please upload a file.");
				return false;
			}
			
			final StepDefinition step = stepTransition
					.defineNextStep("Custom Tool File Download", true)
					.setDescription("Description for 'Custom Tool File Download'");
			
			// repeat all values entered to the user
			SectionDefinition section = step.addSection("Entered details");
			section.readOnlyComponent("Input Field", inputText.getValue());
			section.readOnlyComponent("Date", inputDate.getValueAsString());
			section.readOnlyComponent("Date with Time", inputTime.getValueAsString());
			section.readOnlyComponent("Experience", inputPick.getValue());
			
			section = step.addSection("Uploaded File");
			section.readOnlyComponent("Name", inputName.getValue());
			section.readOnlyComponent("Password", inputPssw.getValue());	// such a thing should of course NOT be done!
			section.readOnlyComponent("Uploaded file name", inputFile.getFile().getFileName());
			section.readOnlyComponent("Uploaded file size", inputFile.getFile().getFileSizeFormatted());
			section.readOnlyComponent("Uploaded file mime", inputFile.getFile().getMimeType());
			
			// add download button
			section = step.addSection("Download File").setDescription("The file as uploaded with a new file name");
			inputFilename = section.textBoxComponent("New filename", inputFile.getFile().getFileName());
			section.fileDownloadComponent("", (ctx, outputFile) -> {
				if (! inputFilename.hasValue()) {
					ctx.getContext().getReporter().error("Please provide a filename");
					return false;
				}
				
				outputFile.setFileName(inputFilename.getValueTrimmed());
				outputFile.setBytes(inputFile.getFile().getBytes());
				outputFile.setMimeType(inputFile.getFile().getMimeType());
				
				return true;
			});
			
			step.setFinishCloseLabel("Close");
			return true;
		}

		@Override
		public boolean onFinish(OnFinish onFinish) {
			// this is usually the time to do the "heavy lifting" of the wizard 
			return true;
		}
	}
}
