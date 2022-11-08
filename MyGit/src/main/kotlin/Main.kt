package svcs

import java.io.*
import java.security.MessageDigest

//fun Array<String>.input(cmd: String, default: String): String = this.indexOf(cmd).let { if ( it > -1) (if (1 + it < this.size && this[it + 1][0] != '-') this[it + 1] else throw java.lang.Exception("Error and error")) else default}

fun String.toSHA256(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(this.toByteArray())
    return bytes.toHex()
}

fun ByteArray.toHex(): String {
    return joinToString("") { "%02x".format(it) }
}

fun ByteArray.toSHA256(): String {
    return MessageDigest.getInstance("SHA-256").digest(this).toHex()
}

fun isSameContentOfFilesBySHA256(a: String, b: String): Boolean {
    return File(a).readBytes().toSHA256() == File(b).readBytes().toSHA256()
}

open class Command(
    val name: String,
    val description: String
) {
    open fun runCmd(args: Array<String>){
        println(description)
    }
}

class CMDCheckout(name: String, description: String): Command(name,description) {
    override fun runCmd(args: Array<String>){
        if (args.size == 1) {
            println("Commit id was not passed.")
            return
        }
        val storeFolder = File("./${commands.getCMDCommit().storePath}/${args[1]}")
        if (!storeFolder.exists()) {
            println("Commit does not exist.")
            return
        }
        storeFolder.walkTopDown().forEach { if (!it.isDirectory) it.copyTo(File("./${it.name}"),true) }
        println("Switched to commit ${args[1]}.")
    }
}

class CMDCommit(
    name: String,
    description: String,
    val storePath: String
) : Command(name,description) {
    override fun runCmd(args: Array<String>){
        if (args.size == 1) {
            println("Message was not passed.")
            return
        }
        val files = commands.getCMDAdd().storeFile.readLines()
        val lastCommit = commands.getCMDLog().getLastCommitHash()
        val changesFiles = if (files.isNotEmpty() && lastCommit.isNotEmpty()) files.filter { !isSameContentOfFilesBySHA256("./$it", "./$storePath/$lastCommit/$it") } else files
        if (changesFiles.isEmpty()) {
            println("Nothing to commit.")
            return
        }
        val hashCommit = (System.currentTimeMillis().toString()+args[1]).toSHA256()
        val storeFolder = File("./$storePath/$hashCommit")
        if (!storeFolder.exists()) storeFolder.mkdir()
        for (fn in files) File("./$fn").copyTo(File("./$storePath/$hashCommit/$fn"))
        commands.getCMDLog().addCommit(hashCommit,args[1])
        println("Changes are committed.")
    }
}

open class CommandStore(
    name: String,
    description: String,
    storeFileName: String
) : Command(name,description) {
    lateinit var storeFile: File

    init {
        if (storeFileName != "") {
            storeFile = File(storeFileName)
            if (!storeFile.parentFile.exists()) storeFile.parentFile.mkdir()
            if (!storeFile.exists()) storeFile.writeText("")
        }
    }

    override fun runCmd(args: Array<String>){
    }
}

class CMDConfig(name: String,description: String,storeFileName: String = "") : CommandStore(name,description,storeFileName) {
    override fun runCmd(args: Array<String>){
        super.runCmd(args)
        if (args.size > 1) storeFile.writeText(args[1])
        val userName = storeFile.readText()
        println(if (userName == "") "Please, tell me who you are." else "The username is $userName.")
    }

    fun getUserName(): String {
        return storeFile.readText()
    }
}

class CMDAdd(name: String,description: String,storeFileName: String = "") : CommandStore(name,description,storeFileName) {
    override fun runCmd(args: Array<String>){
        super.runCmd(args)
        val files = storeFile.readLines()
        if (args.size > 1) {
            if (!File(args[1]).exists()) {
                println("Can't find '${args[1]}'.")
                return
            }
            if (!files.contains(args[1])) storeFile.appendText(args[1]+"\n")
            println("The file '${args[1]}' is tracked.")
        } else {
            println(if (files.isEmpty()) "Add a file to the index." else "Tracked files:\n"+files.joinToString("\n"))
        }
    }
}

class CMDLog(name: String,description: String,storeFileName: String = "") : CommandStore(name,description,storeFileName) {
    override fun runCmd(args: Array<String>){
        super.runCmd(args)
        val commints = storeFile.readLines()
        println(if (commints.isEmpty()) "No commits yet." else commints.joinToString("\n"))
    }

    fun getLastCommitHash(): String {
        val commints = storeFile.readLines()
        return if (commints.size < 3) "" else commints[commints.size -3].split(" ")[1]
    }

    fun addCommit(hash: String, message: String) {
        storeFile.writeText("""
commit $hash
Author: ${commands.getCMDConfig().getUserName()}
$message
""" + storeFile.readText())
    }
}

class Commands {
    var exists: Array<Command> = arrayOf(
        CMDConfig("config", "Get and set a username.", "./vcs/config.txt"),
        CMDAdd("add", "Add a file to the index.","./vcs/index.txt"),
        CMDLog("log", "Show commit logs.","./vcs/log.txt"),
        CMDCommit("commit", "Save changes.","./vcs/commits"),
        CMDCheckout("checkout", "Restore a file.")
    )

    fun existsString(): String {
        return exists.joinToString("\n") { String.format("%-10s %s", it.name, it.description) }
    }

    fun getCMDLog(): CMDLog {
        return exists.find { it.name == "log" } as CMDLog
    }
    fun getCMDAdd(): CMDAdd {
        return exists.find { it.name == "add" } as CMDAdd
    }
    fun getCMDConfig(): CMDConfig {
        return exists.find { it.name == "config" } as CMDConfig
    }
    fun getCMDCommit(): CMDCommit {
        return exists.find { it.name == "commit" } as CMDCommit
    }
}

val commands = Commands()

fun checkIsHelp(args: Array<String>): Boolean {
    if (args.isNotEmpty() && !args.contains("--help")) return false
    println("These are SVCS commands:")
    println(commands.existsString())
    return true
}

fun main(args: Array<String>) {
    if (checkIsHelp(args)) return
    val findCmd = commands.exists.find { it.name == args[0] }
    if (findCmd == null) {
        println("'${args[0]}' is not a SVCS command.")
        return
    }
    findCmd.runCmd(args)
}