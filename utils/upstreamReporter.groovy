import groovy.json.*

URL apiUrl = new URL("https://bitly.com/openjdk11014")
lines = apiUrl.text.split('\n')

def release = "11"
def releaseLog = false
def endMarkCnt = 0
def currentHeader = null

def CVEfix = []
def nonCVEfix = []
def upstreamUpdates = []

def headers = [
    "8" : ["client-libs", "core-", "hotspot", "security-", "xml", "other-libs"],
    "11" : ["* New features", "* Security fixes", "* Other changes"]
]

def trim = [
    "11" : "New in release OpenJDK ",
    "8" : "ALL FIXED ISSUES, BY COMPONENT AND PRIORITY:"
]

for (line in lines) {
    if (!releaseLog && line.startsWith(trim.get(release))) {
        releaseLog = true
    } else if (releaseLog) {
        def oldHead = currentHeader
        for (head in headers.get(release)) {
            if (line.startsWith(head)) {
                currentHeader = head
            } 
        }
        if (currentHeader == oldHead && oldHead != null) {
            if (currentHeader.startsWith("security") || currentHeader.startsWith("* Security")) {
                if (line.contains("CVE"))
                    CVEfix.add(line)
                else
                    nonCVEfix.add(line)
            } else {
                if (line.contains("- JDK"))
                    upstreamUpdates.add(line)
            }
        }
    }
}


println "CVE Security Fix"
println "| **CVE**     |"
println "| ---         |"
for (line in CVEfix) {
    println "|" + line + "|"
}

println "Non-CVE Security Fix"
println "| **Non-CVE**     |"
println "| ---         |"
for (line in nonCVEfix) {
    println "|" + line + "|"
}

println "Upstream Updates"
println "| **Upstream-Patches**     |"
println "| ---         |"
for (line in upstreamUpdates) {
    println "|" + line + "|"
}
