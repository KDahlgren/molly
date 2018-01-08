# notes on the molly hack

This branch (creatively dubbed "hacked-Molly") is dedicated to hosting a hacked version of the original Molly implementation designed to input raw C4 Overlog programs output by iapyx, perform the LDFI fault hypothesis identification workflow, and output the evaluation results for further processing.

# notes on hack locations and reasoning

The hacks occur in only a few places throughout the code. The biggest changes include bypassing the Dedalus and provenance rewriting steps, bypassing the Molly type inference system, and outputting the evaluation results to files.

<b>SyncFTChecker.scala</b>
<br>
The demarcated code decorated with my initials (KD) specifies the logic necessary to bypass the Dedalus and provenance rewrites core to the Molly workflow. The step is crucial because iapyx performs all the necessary rewrites already to output valid C4 Overlog translations and attempting to perform another set of Dedalus and provenance rewrites over such a program breaks Molly's syntax checks.
<br><br>

<b>DedalusTyper.scala</b>
<br>
The three un-commented sections of appropriately annotated code, further identified with my initials, encapsulate the logic needed to bypass the data type inference system utilized in Molly when translating Dedalus programs into C4 Overlog. Instead, hacked-Molly takes advantage of the fact that iapyx already performs type inference to correctly map relation columns to data types. Specifically, the code inputs a mapping of relations and columns to data types as serialed in the "iapyx_types.data" file of the current working directory. The location of "iapyx_types.data" is hard-coded and the file must exist prior to running hacked-Molly. Subsequent code focuses on reading in the tables, making the correct mappings, and saving the maps to the Table data structure.
<br><br>


<b>C4Wrapper.scala</b>
<br>
The appropriately demarcated code inputs a list of tables from the "iapyx_tables.data" file located in the current working directory and iteratively extracts the evaluation results per table before saving the data to an "eval_results_file_#.txt" file, also located in the current working directory. The locations of both the input iapyx_tables.data and output eval_results_file_#.txt files are hard-coded and the files must exist prior to running hacked-Molly. The hack also changes the signature of the C4Wrapper object to also specify the identification number of the current run of the LDFI workflow. 

# instructions

To run iapyx programs, input the complete iapyx olg program in the hacked-Molly command line. For example:
```
sbt "run-main edu.berkeley.cs.boom.molly.SyncFTChecker \
	./iapyx_prog.olg \
	--EOT 4 \
	--EFF 2 \
	--nodes a,b,c \
	--crashes 0 \
	--prov-diagrams"
```
