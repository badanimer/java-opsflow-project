/**
 * A small made-up application that breaks each rule of the package dependencies once, and keeps each
 * rule once. {@code PackageDependencyRulesTest} runs the rules over it, to show that a rule that passes
 * for the real code would have failed for a mistake. It is outside {@code com.opsflow}, so Spring never
 * loads it.
 */
package rulefixture;
