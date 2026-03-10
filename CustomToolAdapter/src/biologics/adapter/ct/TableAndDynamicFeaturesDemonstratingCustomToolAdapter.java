package biologics.adapter.ct;

import genedata.bx.adapter.ct.CustomToolAdapter;
import genedata.bx.adapter.ct.CustomToolCallback;
import genedata.bx.adapter.ct.CustomToolOptions;
import genedata.bx.adapter.ct.ui.SectionDefinition;
import genedata.bx.adapter.ct.ui.StepDefinition;
import genedata.bx.adapter.ct.ui.component.TableComponent;
import genedata.bx.adapter.ct.ui.wizard.OnFinish;
import genedata.bx.adapter.ct.ui.wizard.OnStart;
import genedata.bx.adapter.ct.ui.wizard.Values;
import genedata.bx.adapter.ct.ui.wizard.Wizard;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Map;

public class TableAndDynamicFeaturesDemonstratingCustomToolAdapter implements CustomToolAdapter {
	@Override
	public void options(CustomToolOptions options) {
		// nothing to do
	}

	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// nothing to do
	}

	@Override
	public void perform(CustomToolCallback biologics) {
		biologics.viaWizard(new SingleStepWizard());
	}



	static class SingleStepWizard implements Wizard {
		@Override
		public boolean onStart(OnStart onStart) {
			onStart.setOptionalNumberOfTotalSteps(1);
			final StepDefinition step = onStart.defineNextStep("OneAndOnlyStep", true).setDescription("Description for OneAndOnlyStep");
			attachComponents(step);
			return true;
		}

		@Override
		public boolean onFinish(OnFinish onFinish) {
			Values.Value validationToFailValue = onFinish.getCurrentPageValues().getValueById("validationToFail");
			if(safeBoolean(validationToFailValue)) {
				onFinish.getContext().getReporter().error("Validation to fail is marked as 'true'.");
				return false;
			}

			Values.Value redirectToExternalPageValue = onFinish.getCurrentPageValues().getValueById("redirectToExternalPage");
			if(safeBoolean(redirectToExternalPageValue)) {
				onFinish.setOptionalRedirectURI("https://www.genedata.com");
			}

			onFinish.getContext().getReporter().info("Done, all fine.");
			return true;
		}
	}

	private static boolean safeBoolean(Values.Value v) {
		return v != null && v.getBooleanValue() != null && v.getBooleanValue().booleanValue();
	}
	
	static void attachComponents(StepDefinition step) {
		attachTablesSection(step);
		attachValueChangedHandlerSection(step);
		attachActionButtonSection(step);
	}

	private static void attachValueChangedHandlerSection(StepDefinition step) {
		final SectionDefinition section = step.addSection("Section with attribute dependencies");

		final var textBoxNoDefault = section.textBoxComponent("TextBox (no default value)");
		final var textBoxWithDefault = section.textBoxComponent("TextBox (with default value)", "default value");
		final var dateTimeNoDefault = section.dateTimeComponent("Date (no default value)");
		final var dateTimeWithDefault = section.dateTimeComponent("Date (with default value)", ZonedDateTime.now());
		final var readOnly = section.readOnlyComponent("ReadOnly", "read-only inital value");
		final var dependentOptions = section.singleSelectionListComponent("Dependent options", "Please select first...");

		final var selection = section.singleSelectionListComponent("Drop Down Box Dependency", "one", "two", "three", "exception");
		selection.registerEventHandler((e, callback) -> {
			final String newValue = e.getValue().getStringValue();
			textBoxNoDefault.setValue(String.format("textBoxNoDefault-%s", newValue));
			textBoxWithDefault.setValue(String.format("textBoxWithDefault-%s", newValue));
			dateTimeNoDefault.setValue(ZonedDateTime.now());
			dateTimeWithDefault.setValue(ZonedDateTime.now());
			readOnly.setValue("read-only changed value");

			dependentOptions.clearOptions();
			dependentOptions.addOption(String.format("a-%s", newValue));
			dependentOptions.addOption(String.format("b-%s", newValue));
			dependentOptions.addOption(String.format("c-%s", newValue));
			dependentOptions.addOption(String.format("d-%s", newValue));

			if("three".equals(newValue)) {
				callback.getReporter().warn("The mighty three has been selected!");
			}
			if("exception".equals(newValue)) {
				throw new NullPointerException("NPE raised to see whether exceptions are properly handled");
			}
		});
	}

	private static void attachActionButtonSection(StepDefinition step) {
		final SectionDefinition section = step.addSection("Section with action buttons");

		final var textBoxNoDefault = section.textBoxComponent("TextBox (no default value)");
		final var textBoxWithDefault = section.textBoxComponent("TextBox (with default value)", "default value");
		final var readOnly = section.readOnlyComponent("ReadOnly", "read-only inital value");

		section.actionButtonComponent("Action button, info message", callback -> {
			textBoxNoDefault.setValue("Clicked (info)!");
			textBoxWithDefault.setValue("Clicked (info)!");
			readOnly.setValue("Clicked (info)!");

			callback.getReporter().info("Clicked (info)!");
		});

		section.actionButtonComponent("Action button, error message", callback -> {
			textBoxNoDefault.setValue("Clicked (error)!");
			textBoxWithDefault.setValue("Clicked (error)!");
			readOnly.setValue("Clicked (error)!");

			callback.getReporter().error("Clicked (error)!");
		});

		section.actionButtonComponent("Action button, exception", callback -> {
			textBoxNoDefault.setValue("Clicked (exception)!");
			textBoxWithDefault.setValue("Clicked (exception)!");
			readOnly.setValue("Clicked (exception)!");

			callback.getReporter().error("Clicked (exception)!");
			throw new NullPointerException("NPE raised to see whether exceptions are properly handled");
		});
	}

	private static void attachTablesSection(StepDefinition step) {
		final SectionDefinition section = step.addSection("Section with tables");
		attachStorybookTable(section);
	}

	private static void attachStorybookTable(SectionDefinition section) {
		final TableComponent table = section.tableComponent("Storybook table; 1st row with label, default values and descriptions; 2nd row without label, no default values and without descriptions");

		final var colTextBox = table.addColumn("TextBox Column");
		final var colMultiSelection = table.addColumn("Multi Selection Column");
		final var colMultiCheckbox = table.addColumn("Multi Checkbox Column");
		final var colDateTime = table.addColumn("DateTime Column");

		final var row1 = table.addRow("1st row");
		row1.cell(colTextBox).textBoxComponent("TextBox (with default value)", "default value");
		row1.cell(colMultiSelection).multiSelectionListComponent("MultiSelectionList (with pre-selection)", Arrays.asList("one", "two", "three"), Arrays.asList("one", "three"));
		row1.cell(colMultiCheckbox).multiSelectionCheckboxComponent("MultiSelectionCheckbox (with pre-selection)", Arrays.asList("one", "two", "three"), Arrays.asList("one", "three"));
		row1.cell(colDateTime).dateTimeComponent("DateTime (with default value)", ZonedDateTime.now());

		final var row2 = table.addRow();
		row2.cell(colTextBox).textBoxComponent();
		row2.cell(colMultiSelection).multiSelectionListComponent(Arrays.asList("one", "two", "three"));
		row2.cell(colMultiCheckbox).multiSelectionCheckboxComponent(Arrays.asList("one", "two", "three"));
		row2.cell(colDateTime).dateTimeComponent();
	}

	static void attachRedirectionBehaviorComponent(StepDefinition step) {
		final SectionDefinition section = step.addSection("Redirection behavior section");
		section.booleanComponent("Redirect to external page?", false).setId("redirectToExternalPage");
	}

}
