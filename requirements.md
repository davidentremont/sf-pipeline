# Salesforce Async Data Pipeline
A local webapp to run large batches against Salesforce based on a PK-chunk query, featuring a flexible in/out plugin framework and adjustable batch size and number of async workers. Leverages the Salesforce DX CLI to run queries and commands.

1. Create webapp and use supplied svg as inspiration. 
2. Allow user to enter the salesforce org username to be passed to the sfdx cli commands.
3. Show a list of jobs that are selectable. Draw source from a local config file for now.
4. Allow dev to create plugins.
    4.1. Plugins should take generic types in that are output of prior step. Step 0 is the query result data.
    4.2. Plugins should have ability to run sfdx cli commands
    Sample plugin found in ShareCalculator.java
5. All dev to create jobs
    5.1. Jobs should require a name, version, and query.
    5.2. Jobs should list plugins to run.
6. Allow user to input the batch size (query limit) and threads (spawned async worker threads) with start ans stop buttons.
7. Show worker status and progress of job for visibility into the processes.
8. When STARTING job run logically:
    8.1. Run a query using sfdx specified in the job and limit the result to the batch size.
    8.2. Divide the returned data into chunks to be consumed by the worker threads.
    8.3. Start async/thread/background workers
        8.3.1 Each worker gets data chunked from query.
        8.3.2 Worker loops through plugins, passing the output of one as the input to the next in a chain.
        8.3.3 Upon completion of plugins steps for each worker, report back to main thread.
    8.4. Upon completion of worker threads, query next batch of data using the last ID returned "WHERE Id > :lastID LIMIT :btchSize"
    8.5 Repeat until main thread query returns no rows or user clicks STOP.