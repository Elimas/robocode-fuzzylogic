package custom;

import com.fuzzylite.Engine;
import com.fuzzylite.norm.s.Maximum;
import com.fuzzylite.norm.t.Minimum;
import com.fuzzylite.rule.Rule;
import com.fuzzylite.rule.RuleBlock;
import com.fuzzylite.term.Triangle;
import com.fuzzylite.variable.InputVariable;
import com.fuzzylite.variable.OutputVariable;
import javafx.util.Pair;
import org.joda.time.DateTime;
import robocode.*;

import java.util.ArrayList;

import static custom.Utils.*;

public class FuzzyRobot extends AdvancedRobot {

    private static final int DMG_TIME_S = 5;
    private double lastRecentDmg = 0;
    private ArrayList<Pair<DateTime, Double>> dmg = new ArrayList<>();
    private int moveDir = 1;
    private int wallTimer = 0;
    private AdvancedEnemyBot enemy = new AdvancedEnemyBot();

    private Engine engine;
    private InputVariable dmgTaken = new InputVariable();
    private InputVariable enemyDistance = new InputVariable();
    private OutputVariable runSpeed = new OutputVariable();

    public FuzzyRobot() {
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

        enemyDistance.setName("EnemyDistance");
        enemyDistance.setRange(0.0, 1200.0); //1200px - maksymalny zasieg radaru
        enemyDistance.addTerm(new Triangle("Short", 0, 300, 600));
        enemyDistance.addTerm(new Triangle("Medium", 300, 600, 800));
        enemyDistance.addTerm(new Triangle("Far", 600, 800, 1200));
        engine.addInputVariable(enemyDistance);

        runSpeed = new OutputVariable();
        runSpeed.setDefaultValue(Double.NaN);
        runSpeed.setName("RunSpeed");
        runSpeed.setRange(0.000, 1.000);
        runSpeed.addTerm(new Triangle("Low", 0, 0.25, 0.5));
        runSpeed.addTerm(new Triangle("Medium", 0.25, 0.5, 0.7));
        runSpeed.addTerm(new Triangle("High", 0.5, 0.75, 1));
        engine.addOutputVariable(runSpeed);

        RuleBlock ruleBlock = new RuleBlock();
        ruleBlock.setConjunction(new Minimum());
        ruleBlock.setDisjunction(new Maximum());

        ruleBlock.addRule(Rule.parse("if DmgTaken is Low and EnemyDistance is Far then RunSpeed is Low", engine));
        ruleBlock.addRule(Rule.parse("if DmgTaken is Medium or EnemyDistance is Medium then RunSpeed is Medium", engine));
        ruleBlock.addRule(Rule.parse("if DmgTaken is High then RunSpeed is High", engine));
        engine.addRuleBlock(ruleBlock);

        engine.configure("Minimum", "Maximum", "Minimum", "Maximum", "Centroid");

        StringBuilder status = new StringBuilder();
        if (!engine.isReady(status)) {
            throw new RuntimeException("Engine not ready. "
                    + "The following errors were encountered:\n" + status.toString());
        }
    }

    public void run() {
        setAdjustRadarForGunTurn(true);
        setAdjustGunForRobotTurn(true);
        enemy.reset();
        setTurnRadarRight(360);
        while (true) {
            engine.process();

            // rotate the radar
            setTurnRadarRight(360);
            dmgTaken.setInputValue(clamp(recentDmg() / 8., 0, 1));

            if (!Double.isNaN(runSpeed.getOutputValue())) setMaxVelocity(runSpeed.getOutputValue() * Rules.MAX_VELOCITY);

            if (wallTimer > 0) wallTimer--;
            double newX = getX() + Math.cos(getHeading()) * getVelocity();
            double newY = getY() + Math.sin(getHeading()) * getVelocity();
            if ((newX + getWidth() > getBattleFieldWidth()) || newX - getWidth() < 0 || newY - getHeight() < 0 || (newY + getHeight() > getBattleFieldHeight())) {
                if (wallTimer <= 0) {
                    moveDir *= -1;
                    setAhead(moveDir * 200);
                    wallTimer = 100;
                }
            }
            updateGun();
            execute();
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        setTurnRight(e.getBearing() + 90);

        enemyDistance.setInputValue(e.getDistance());
        System.out.println(e.getDistance());

        if ( enemy.none() || e.getDistance() < enemy.getDistance() - 70 || e.getName().equals(enemy.getName())) {
            enemy.update(e, this);
        }
    }

    @Override
    public void onHitByBullet(HitByBulletEvent event) {
        super.onHitByBullet(event);
        dmg.add(new Pair<>(new DateTime(), event.getPower()));
        int diff = 15;
        setTurnLeft(0);
        setTurnRight(0);
        if (event.getBearing() >= 90 + diff && event.getBearing() <= 180) {
            setTurnLeft(event.getBearing() - 90);
        } else if (event.getBearing() > 0 && event.getBearing() < 90 - diff) {
            setTurnLeft(90 - event.getBearing());
        } else if (event.getBearing() < 0 && event.getBearing() > -90 + diff) {
            setTurnRight(90 - event.getBearing());
        } else if (event.getBearing() <= -90 - diff && event.getBearing() >= -180) {
            setTurnRight(event.getBearing() + 90);
        }

        setAhead(moveDir * 100);
    }

    public double recentDmg() {
        DateTime after = new DateTime().minusSeconds(DMG_TIME_S);
        lastRecentDmg = (int) dmg.stream()
                .filter(n -> n.getKey().isAfter(after))
                .mapToDouble(Pair::getValue)
                .sum();
        return lastRecentDmg;
    }

    void updateGun() {
        if (enemy.none())
            return;

        double firePower = Math.min(500 / enemy.getDistance(), 3);
        double bulletSpeed = 20 - firePower * 3;
        long time = (long)(enemy.getDistance() / bulletSpeed);

        double futureX = enemy.getFutureX(time);
        double futureY = enemy.getFutureY(time);
        double absDeg = absoluteBearing(getX(), getY(), futureX, futureY);

        setTurnGunRight(normalizeBearing(absDeg - getGunHeading()));

        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
            setFire(firePower);
        }
    }

    @Override
    public void onRobotDeath(RobotDeathEvent e) {
        if (e.getName().equals(enemy.getName())) {
            enemy.reset();
        }
    }
}
