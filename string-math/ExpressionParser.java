import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public class ExpressionParser {

    // check: x + ((4 - 2^3 + 1) * -sqrt(3*3+4*4)) / 2 = 11.5

    public static void main(String[] args) {
        final ExpressionParser parser = new ExpressionParser("x + ((4 - 2^3 + 1) * -sqrt(3*3+4*4)) / 2");
        parser.variable("x", () -> 4);
        final Expression e1 = parser.parse();
        System.out.println(e1.eval());
    }

    private final String expression;
    private final Map<String, Expression> variables = Maps.newConcurrentMap();

    private int currentIndex;
    private int currentChar;

    public ExpressionParser(final String expression) {
        this.expression = expression;

        this.variables.put("e", () -> Math.E);
        this.variables.put("π", () -> Math.PI);
        this.variables.put("pi", () -> Math.PI);
    }

    public Expression parse() {
        this.reset();
        this.nextChar();
        final Expression x = this.parseExpression();

        if (this.currentIndex < expression.length()) throw new RuntimeException("Unexpected: " + (char) this.currentChar);

        return x;
    }

    private void reset() {
        this.currentIndex = -1;
        this.currentChar = 0;
    }

    // math parsers

    private Expression parseExpression() {
        Expression x = this.parseTerm();

        while (true) {
            if (this.eat('+')) { // addition
                final Expression a = x,
                                 b = this.parseTerm();
                x = () -> a.eval() + b.eval();
                continue;
            }

            if (this.eat('-')) { // subtraction
                final Expression a = x,
                                 b = this.parseTerm();
                x = () -> a.eval() - b.eval();
                continue;
            }

            return x;
        }
    }

    private Expression parseTerm() {
        Expression x = this.parseFactor();
        while (true) {
            if (this.eat('*')) { // multiplication
                final Expression a = x,
                                 b = this.parseFactor();
                x = () -> a.eval() * b.eval();
                continue;
            }

            if (this.eat('/')) { // division
                final Expression a = x,
                                 b = this.parseFactor();
                x = () -> a.eval() / b.eval();
                continue;
            }

            return x;
        }
    }

    private Expression parseFactor() {
        if (this.eat('+')) { // unary plus
            final Expression e = this.parseFactor();
            return () -> +e.eval();
        }

        if (this.eat('-')) { // unary minus
            final Expression e = this.parseFactor();
            return () -> -e.eval();
        }

        final Expression x = this.parseBase();

        // have to be handled after anything else
        // -> function(234)^4
        //       ^          ^
        //  first call      |
        // the function     |
        //              then this
        if (this.eat('^')) { // exponentiation
            final Expression exponent = this.parseFactor();
            return () -> Math.pow(x.eval(), exponent.eval());
        }

        return x;
    }

    private Expression parseBase() {
        if (this.eat('(')) { // parentheses
            final Expression x = this.parseExpression();

            if (!this.eat(')')) throw new RuntimeException("Missing ')' at " + this.currentIndex);
            return x;
        }

        if (this.isNumeric()) return this.parseNumber(); // numbers
        if (this.isAlphabetic()) return this.parseAlphabetic(); // functions/methods/variables

        throw new RuntimeException("Unexpected: %s at index %d".formatted((char) this.currentChar, this.currentIndex));
    }

    // helper methods

    private void nextChar() {
        this.currentChar = ++this.currentIndex < expression.length() ? expression.charAt(this.currentIndex) : -1;
    }

    private boolean eat(int charToEat) {
        while (this.currentChar == ' ') this.nextChar();
        if (this.currentChar != charToEat) return false;

        this.nextChar();
        return true;

    }

    private boolean isNumeric() {
        return (this.currentChar >= '0' && this.currentChar <= '9') || this.currentChar == '.';
    }

    private boolean isAlphabetic() {
        return this.currentChar >= 'a' && this.currentChar <= 'z';
    }

    // little parsers

    private Expression parseNumber() {
        final double d = Double.parseDouble(this.parseString(this::isNumeric));
        return () -> d;
    }

    private String parseName() {
        return this.parseString(this::isAlphabetic);
    }

    private String parseString(final BooleanSupplier check) {
        final int startPos = this.currentIndex;
        while (check.getAsBoolean()) this.nextChar();
        final int endPos = this.currentIndex;
        return this.expression.substring(startPos, endPos);
    }

    private Expression parseAlphabetic() {
        final String name = this.parseName();

        // only variables are alphabetic without brackets
        if (!this.eat('(')) return this.parseVariable(name);

        // only methods closing them directly
        if (this.eat(')')) return this.parseMethod(name);

        // functions have an expression inside there brackets
        return this.parseFunction(name);
    }

    private Expression parseMethod(final String name) {
        return switch (name) {
            case "random", "rndm" -> Math::random;
            default -> throw new RuntimeException("Unknown method: " + name);
        };
    }

    private Expression parseFunction(final String name) {
        final Expression x = this.parseExpression();
        if (!eat(')')) throw new RuntimeException("Missing ')' after argument to " + name);

        return switch (name) {
            case "sqrt" ->      () -> Math.sqrt(x.eval());
            case "sin" ->       () -> Math.sin(x.eval());
            case "cos" ->       () -> Math.cos(x.eval());
            case "tan" ->       () -> Math.tan(x.eval());
            case "asin" ->      () -> Math.asin(x.eval());
            case "acos" ->      () -> Math.acos(x.eval());
            case "atan" ->      () -> Math.atan(x.eval());
            case "abs" ->       () -> Math.abs(x.eval());
            case "round" ->     () -> Math.round(x.eval());
            case "log" ->       () -> Math.log(x.eval());
            case "degrees" ->   () -> Math.toDegrees(x.eval());
            case "radians" ->   () -> Math.toRadians(x.eval());
            default -> throw new RuntimeException("Unknown function: " + name);
        };
    }

    private Expression parseVariable(final String name) {
        if (this.variables.isEmpty() || !this.variables.containsKey(name)) throw new RuntimeException("Unknown variable: " + name);
        return this.variables.get(name); // this has to be changed if variables should affect already parsed expressions
    }

    // getters

    public String expression() {
        return this.expression;
    }

    // variable stuff

    public Optional<Expression> variable(final String name) {
        return Optional.ofNullable(this.variables.get(name));
    }

    public void variable(final String name, final Expression e) {
        this.variables.put(name, e);
    }

    public void variable(final String name, final double e) {
        this.variables.put(name, () -> e);
    }

}
