// SuperCollider Anthropic API Access using unixCmd and curl

// Configuration
~apiKey = "x";
~apiEndpoint = "https://api.anthropic.com/v1/complete";

// Function to make API call using curl
~makeAnthropicApiCall = { |prompt, maxTokens=100|
    var curlCmd, result;

    // Construct the curl command
    curlCmd = "curl -s -X POST " ++ ~apiEndpoint
    ++ " -H \"Content-Type: application/json\""
    ++ " -H \"x-api-key: " ++ ~apiKey ++ "\""
    ++ " -H \"anthropic-version: 2023-06-01\""
    ++ " -d '{\"prompt\": \"Human: " ++ prompt ++ "\\n\\nAssistant:\", "
    ++ "\"model\": \"claude-2.1\", "
    ++ "\"max_tokens_to_sample\": " ++ maxTokens ++ ", "
    ++ "\"temperature\": 0.7}'";

    // Execute the curl command and capture the output
    result = "";
    curlCmd.unixCmd({ |msg|
        result = result ++ msg;
    });

    // Wait a bit for the command to complete (adjust as needed)
    1.wait;

    // Parse and return the result
    ^result.parseYAML;
};

// Example usage
(
~testAnthropicApiCall = {
    var prompt = "Translate the following English text to French: 'Hello, how are you?'";
    var response = ~makeAnthropicApiCall.(prompt);

    "API Response:".postln;
    response.postln;

    // Extract and print just the completion text
    if(response.notNil and: { response.at('completion').notNil }) {
        "Completion:".postln;
        response.at('completion').postln;
    };
};

// Run the test
~testAnthropicApiCall.fork;
)

// Error handling example
(
~testAnthropicApiCallWithErrorHandling = {
    var prompt = "Translate the following English text to French: 'Hello, how are you?'";
    var response;

    try {
        response = ~makeAnthropicApiCall.(prompt);
        "API Response:".postln;
        response.postln;

        if(response.notNil and: { response.at('completion').notNil }) {
            "Completion:".postln;
            response.at('completion').postln;
        };
    } {
        |error|
        "An error occurred:".postln;
        error.errorString.postln;
    };
};

// Run the test with error handling
~testAnthropicApiCallWithErrorHandling.fork;
)

a = "ls -a".unixCmdGetStdOut
b = a.split(Char.nl)

