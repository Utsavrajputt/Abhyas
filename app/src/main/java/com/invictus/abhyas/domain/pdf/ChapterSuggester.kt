package com.invictus.abhyas.domain.pdf

/**
 * Optional keyword-based chapter hints. Results are always shown as "suggested" and are never
 * applied unless the user accepts them. Chapters already present in the user's library are tried first.
 */
object ChapterSuggester {

    private val BUILTIN: Map<String, Map<String, List<String>>> = mapOf(
        "phys" to mapOf(
            "Kinematics" to listOf("velocity", "acceleration", "projectile", "displacement", "uniform motion"),
            "Laws of Motion" to listOf("friction", "newton", "inertia", "tension", "pseudo force"),
            "Work, Energy and Power" to listOf("work done", "kinetic energy", "potential energy", "power", "conservative"),
            "Rotational Motion" to listOf("torque", "moment of inertia", "angular momentum", "rolling", "angular velocity"),
            "Gravitation" to listOf("gravitational", "orbital", "escape velocity", "kepler", "satellite"),
            "Thermodynamics" to listOf("adiabatic", "isothermal", "heat engine", "carnot", "first law", "entropy"),
            "Oscillations and Waves" to listOf("simple harmonic", "shm", "wave", "frequency", "resonance", "doppler"),
            "Electrostatics" to listOf("electric field", "coulomb", "capacitor", "electric potential", "dipole", "gauss"),
            "Current Electricity" to listOf("resistance", "ohm", "kirchhoff", "drift velocity", "wheatstone", "emf"),
            "Magnetism" to listOf("magnetic field", "biot", "solenoid", "lorentz", "magnetic moment"),
            "Electromagnetic Induction" to listOf("induced emf", "faraday", "lenz", "inductance", "eddy"),
            "Optics" to listOf("lens", "mirror", "refraction", "interference", "diffraction", "prism", "young"),
            "Modern Physics" to listOf("photoelectric", "de broglie", "bohr", "nucleus", "radioactive", "half life", "x-ray"),
            "Semiconductors" to listOf("p-n junction", "diode", "transistor", "semiconductor", "logic gate"),
        ),
        "chem" to mapOf(
            "Some Basic Concepts of Chemistry" to listOf("mole", "molarity", "stoichiometry", "empirical formula", "limiting reagent"),
            "Structure of Atom" to listOf("quantum number", "orbital", "bohr", "electronic configuration", "de broglie"),
            "Chemical Bonding" to listOf("hybridisation", "hybridization", "vsepr", "bond order", "dipole moment", "molecular orbital"),
            "Chemical Equilibrium" to listOf("equilibrium constant", "le chatelier", "kp", "kc", "degree of dissociation"),
            "Ionic Equilibrium" to listOf("ph ", "buffer", "solubility product", "ksp", "hydrolysis"),
            "Chemical Kinetics" to listOf("rate constant", "order of reaction", "half life", "activation energy", "arrhenius"),
            "Electrochemistry" to listOf("electrode potential", "galvanic", "nernst", "electrolysis", "conductivity", "faraday"),
            "Thermodynamics" to listOf("enthalpy", "entropy", "gibbs", "hess", "calorimeter"),
            "Solutions" to listOf("colligative", "raoult", "osmotic", "molality", "vapour pressure", "van't hoff"),
            "Coordination Compounds" to listOf("ligand", "coordination number", "crystal field", "chelate", "werner"),
            "Hydrocarbons" to listOf("alkane", "alkene", "alkyne", "benzene", "markovnikov"),
            "Haloalkanes and Haloarenes" to listOf("haloalkane", "sn1", "sn2", "alkyl halide", "chlorobenzene"),
            "Alcohols, Phenols and Ethers" to listOf("alcohol", "phenol", "ether", "williamson"),
            "Aldehydes, Ketones and Carboxylic Acids" to listOf("aldehyde", "ketone", "carboxylic", "aldol", "cannizzaro"),
            "Amines" to listOf("amine", "diazonium", "hinsberg", "aniline"),
            "Biomolecules" to listOf("glucose", "protein", "amino acid", "nucleic", "vitamin", "enzyme"),
        ),
        "bio" to mapOf(
            "The Living World" to listOf("taxonomy", "binomial", "taxon", "nomenclature"),
            "Cell: The Unit of Life" to listOf("mitochondria", "ribosome", "endoplasmic", "golgi", "plasma membrane", "nucleus"),
            "Cell Cycle and Cell Division" to listOf("mitosis", "meiosis", "metaphase", "prophase", "cytokinesis"),
            "Photosynthesis in Higher Plants" to listOf("photosynthesis", "calvin", "chlorophyll", "c4 plant", "light reaction"),
            "Respiration in Plants" to listOf("glycolysis", "krebs", "electron transport", "fermentation", "respiratory quotient"),
            "Plant Growth and Development" to listOf("auxin", "gibberellin", "cytokinin", "abscisic", "photoperiod"),
            "Human Physiology" to listOf("nephron", "cardiac", "hormone", "neuron", "digestive", "respiratory"),
            "Reproduction" to listOf("pollination", "fertilisation", "fertilization", "gametogenesis", "placenta", "embryo"),
            "Genetics and Evolution" to listOf("mendel", "allele", "genotype", "linkage", "hardy-weinberg", "mutation", "dna replication"),
            "Biotechnology" to listOf("recombinant", "pcr", "restriction enzyme", "vector", "transgenic"),
            "Ecology" to listOf("ecosystem", "population", "food chain", "biodiversity", "ecological succession"),
        ),
        "math" to mapOf(
            "Sets, Relations and Functions" to listOf("relation", "function", "domain", "range", "bijective"),
            "Complex Numbers" to listOf("complex number", "argument", "modulus", "argand", "cube root of unity"),
            "Quadratic Equations" to listOf("quadratic", "discriminant", "roots of the equation"),
            "Sequences and Series" to listOf("arithmetic progression", "geometric progression", " a.p", " g.p", "series"),
            "Permutations and Combinations" to listOf("permutation", "combination", "arrangements", "ways to"),
            "Binomial Theorem" to listOf("binomial", "coefficient of", "expansion"),
            "Limits, Continuity and Differentiability" to listOf("limit", "continuous", "differentiable", "derivative"),
            "Integration" to listOf("integral", "integrate", "definite integral", "antiderivative"),
            "Differential Equations" to listOf("differential equation", "order and degree", "integrating factor"),
            "Straight Lines and Circles" to listOf("straight line", "circle", "tangent to the circle", "slope"),
            "Conic Sections" to listOf("parabola", "ellipse", "hyperbola", "eccentricity"),
            "Vectors and 3D Geometry" to listOf("vector", "direction cosines", "plane", "skew lines", "dot product"),
            "Probability" to listOf("probability", "random variable", "bayes", "dice", "conditional probability"),
            "Matrices and Determinants" to listOf("matrix", "determinant", "adjoint", "inverse of"),
            "Trigonometry" to listOf("sin", "cos", "tan", "trigonometric", "inverse trigonometric"),
        ),
    )

    data class Suggestion(val chapter: String, val score: Int)

    fun suggest(subject: String, statement: String, libraryChapters: List<String> = emptyList()): Suggestion? {
        val text = " " + statement.lowercase() + " "
        // 1) chapters the user already has: match on the chapter's own words.
        var best: Suggestion? = null
        for (ch in libraryChapters) {
            val words = Similarity.tokens(ch).filter { it.length >= 4 }
            val hits = words.count { text.contains(it) }
            if (words.isNotEmpty() && hits >= 1 && hits * 2 >= words.size && hits > (best?.score ?: 0)) best = Suggestion(ch, hits)
        }
        if (best != null) return best
        // 2) built-in keywords for the subject
        val s = subject.lowercase()
        val key = when {
            s.contains("phys") -> "phys"
            s.contains("chem") -> "chem"
            s.contains("bio") || s.contains("botany") || s.contains("zoology") -> "bio"
            s.contains("math") -> "math"
            else -> return null
        }
        for ((chapter, words) in BUILTIN.getValue(key)) {
            val hits = words.count { text.contains(it) }
            if (hits > (best?.score ?: 0)) best = Suggestion(chapter, hits)
        }
        return best
    }
}
