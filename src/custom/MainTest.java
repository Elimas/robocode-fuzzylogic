package custom;

import com.fuzzylite.Engine;
import com.fuzzylite.rule.Rule;
import com.fuzzylite.rule.RuleBlock;
import com.fuzzylite.term.Triangle;
import com.fuzzylite.variable.InputVariable;
import com.fuzzylite.variable.OutputVariable;

/**
 * Created by Elimas on 2016-06-24.
 */
public class MainTest {

    private static Engine engine;
    private static InputVariable dmgTaken = new InputVariable();
    private static OutputVariable runSpeed = new OutputVariable();

    public static void main(String[] args) {
        try {
            engine = new Engine();
            engine.setName("FuzzyRobot");

        /*
        Obrażenia w ciągu ostatnich 10s im będą większe tym szybciej robot będzie uciekał w kierunku 90 +/- 45 od pocisku
         */
            dmgTaken = new InputVariable();
            dmgTaken.setName("DmgTaken");
            dmgTaken.setRange(0.000, 1.000);
            dmgTaken.addTerm(new Triangle("Low", 0, 0.25, 0.5));
            dmgTaken.addTerm(new Triangle("Medium", 0.25, 0.5, 0.7));
            dmgTaken.addTerm(new Triangle("High", 0.5, 0.75, 1));
            engine.addInputVariable(dmgTaken);

            runSpeed = new OutputVariable();
            runSpeed.setName("RunSpeed");
            runSpeed.setRange(0.000, 1.000);
            runSpeed.addTerm(new Triangle("Low", 0, 0.25, 0.5));
            runSpeed.addTerm(new Triangle("Medium", 0.25, 0.5, 0.7));
            runSpeed.addTerm(new Triangle("High", 0.5, 0.75, 1));
            engine.addOutputVariable(runSpeed);

            RuleBlock ruleBlock = new RuleBlock();
            ruleBlock.addRule(Rule.parse("if DmgTaken is Low then RunSpeed is Low", engine));
            ruleBlock.addRule(Rule.parse("if DmgTaken is Medium then RunSpeed is Medium", engine));
            ruleBlock.addRule(Rule.parse("if DmgTaken is High then RunSpeed is High", engine));
            engine.addRuleBlock(ruleBlock);

            engine.configure("", "", "Minimum", "Maximum", "Centroid");

            StringBuilder status = new StringBuilder();
            if (!engine.isReady(status)) {
                throw new RuntimeException("Engine not ready. "
                        + "The following errors were encountered:\n" + status.toString());
            }
            System.out.println("OK");
        } catch (Throwable e) {
            System.err.println("Error when loading FuzzyLite:");
            e.printStackTrace();
        }

    }
}
