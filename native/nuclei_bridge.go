package main

import (
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"strings"
)

type NucleiResult struct {
	TemplateID string `json:"template-id"`
	Severity   string `json:"info.severity"`
	Name       string `json:"info.name"`
	Matched    string `json:"matched-at"`
	Extractor  string `json:"extracted-results"`
}

type NucleiScanOutput struct {
	Target   string          `json:"target"`
	Template string          `json:"template"`
	Results  []NucleiResult  `json:"results"`
	Errors   []string        `json:"errors"`
}

func ScanTargetWithNuclei(target string, templatePath string) string {
	output := NucleiScanOutput{
		Target:   target,
		Template: templatePath,
		Results:  []NucleiResult{},
		Errors:   []string{},
	}

	nucleiPath := "nuclei"
	if envPath := os.Getenv("NUCLEI_PATH"); envPath != "" {
		nucleiPath = envPath
	}

	args := []string{"-target", target, "-jsonl", "-silent", "-timeout", "10", "-retries", "1"}
	if templatePath != "" {
		args = append(args, "-t", templatePath)
	}

	cmd := exec.Command(nucleiPath, args...)
	var stdout, stderr strings.Builder
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	err := cmd.Run()
	if err != nil && err.Error() != "exit status 1" {
		output.Errors = append(output.Errors, fmt.Sprintf("nuclei execution error: %v", err.Error()))
	}

	if stderr.Len() > 0 {
		for _, line := range strings.Split(stderr.String(), "\n") {
			line = strings.TrimSpace(line)
			if line != "" {
				output.Errors = append(output.Errors, line)
			}
		}
	}

	if stdout.Len() > 0 {
		for _, line := range strings.Split(stdout.String(), "\n") {
			line = strings.TrimSpace(line)
			if line == "" {
				continue
			}
			var result NucleiResult
			if err := json.Unmarshal([]byte(line), &result); err == nil {
				output.Results = append(output.Results, result)
			}
		}
	}

	jsonBytes, _ := json.MarshalIndent(output, "", "  ")
	return string(jsonBytes)
}

func main() {
	target := "http://example.com"
	template := ""

	if len(os.Args) > 1 {
		target = os.Args[1]
	}
	if len(os.Args) > 2 {
		template = os.Args[2]
	}

	fmt.Println(ScanTargetWithNuclei(target, template))
}
