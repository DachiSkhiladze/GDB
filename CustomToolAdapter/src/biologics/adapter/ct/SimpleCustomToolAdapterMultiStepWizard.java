package biologics.adapter.ct;

import genedata.bx.adapter.Role;
import genedata.bx.adapter.ct.CustomToolAdapter;
import genedata.bx.adapter.ct.CustomToolAdapter.RequiresRole;
import genedata.bx.adapter.ct.CustomToolCallback;
import genedata.bx.adapter.ct.CustomToolOptions;
import genedata.bx.adapter.ct.ui.SectionDefinition;
import genedata.bx.adapter.ct.ui.StepDefinition;
import genedata.bx.adapter.ct.ui.component.InputTextAreaComponent;
import genedata.bx.adapter.ct.ui.component.InputTextBoxComponent;
import genedata.bx.adapter.ct.ui.component.InputTextMultiSelectionComponent;
import genedata.bx.adapter.ct.ui.wizard.OnFinish;
import genedata.bx.adapter.ct.ui.wizard.OnStart;
import genedata.bx.adapter.ct.ui.wizard.StepTransition;
import genedata.bx.adapter.ct.ui.wizard.Wizard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simple custom tool adapter exemplifying a multi-step wizard, with the number
 * of steps decided after the first page.
 * <p>
 * The {@link RequiresRole} annotation makes this adapter available only to users with either Administrator or GeneralEditorUser role.
 *
 * <div style="font-size:x-small">
 * Copyright 2022 Genedata AG. All Rights Reserved.
 * </div>
 */
@RequiresRole( roles = { Role.Administrator, Role.GeneralEditorUser } )
public class SimpleCustomToolAdapterMultiStepWizard implements CustomToolAdapter {
	
	private static final String PAGE_TITLE = "Custom Tool Multi Step Wizard";
	private static final String PAGE_DESCRIPTION = "Description for 'Custom Tool Multi Step Wizard'";
	
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
		biologics.viaWizard(new SimpleMultiStepWizard());
	}
	
	static class SimpleMultiStepWizard implements Wizard {
		private InputTextMultiSelectionComponent inputSelectType;
		private InputTextBoxComponent inputTextRounds;
		private List<InputTextAreaComponent> inputTextInfoPerRound = new ArrayList<>();
		
		@Override
		public boolean onStart(OnStart onStart) {
			final StepDefinition step = onStart
					.defineNextStep(PAGE_TITLE, false)
					.setDescription(PAGE_DESCRIPTION);
			
			SectionDefinition section = step.addSection("Panning Protocol details");
			inputSelectType = section.singleSelectionCheckboxComponent("Type", "Differential", "Solution", "Solid Phase", "Whole Cell");
			inputTextRounds = section.textBoxComponent("Number of Rounds").setDefaultValue("3");
			return true;
		}

		@Override
		public boolean onNext(StepTransition stepTransition) {
			// validation on all steps
			if (! inputSelectType.hasValue()) {
				stepTransition.getContext().getReporter().error("Please select the Panning Protocol Type.");
				return false;
			}
			if (! inputTextRounds.hasValue() || ! inputTextRounds.getValueTrimmed().matches("\\d+")) {
				stepTransition.getContext().getReporter().error("Number of Panning Protocol Rounds is not a valid number.");
				return false;
			}
			int cnt = Integer.parseInt(inputTextRounds.getValueTrimmed());
			
			// remove input boxes if user went back and forth
			int i = inputTextInfoPerRound.size();
			while (i >= stepTransition.getCurrentStepNumber()) inputTextInfoPerRound.remove(--i);
			
			// add one page per round requesting additional information from the user
			if (cnt >= stepTransition.getCurrentStepNumber()) {
				final StepDefinition step = stepTransition
					.setOptionalNumberOfTotalSteps(cnt + 2)
					.defineNextStep(PAGE_TITLE, false)
					.setDescription(PAGE_DESCRIPTION);
				int round = stepTransition.getCurrentStepNumber();
				
				SectionDefinition section = step.addSection("Enter additional information for Round "+round);
				inputTextInfoPerRound.add(section.textAreaComponent("Details for Round "+round));
				return true;
			}
			// add final page listing all information about the panning protocol 
			else {
				final StepDefinition step = stepTransition
						.setOptionalNumberOfTotalSteps(cnt + 2)
						.defineNextStep(PAGE_TITLE, true)
						.setDescription(PAGE_DESCRIPTION);
					
				SectionDefinition section = step.addSection("Panning Protocol details");
				section.readOnlyComponent("Type", inputSelectType.getValue());
				for (int r=0; r<inputTextInfoPerRound.size(); r++) {
					section.readOnlyComponent("Round "+(r+1), inputTextInfoPerRound.get(r).getValue());
				}
				
				step.setFinishCloseLabel("Close");
				return true;
			}
		}

		@Override
		public boolean onFinish(OnFinish onFinish) {
			onFinish.getContext().getReporter().info("#0 Panning Protocol had #1 Rounds.",
					inputSelectType.getValue(),
					inputTextInfoPerRound.size());
			return true;
		}
	}
}
