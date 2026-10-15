"""Portable synthetic RAG regression. No service credentials or historical artifacts."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import uuid


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "target" / ("public-offline-" + uuid.uuid4().hex)


def main():
    OUTPUT.mkdir(parents=True)
    allowed = {
        "PATH", "JAVA_HOME", "HOME", "USERPROFILE", "SYSTEMROOT", "WINDIR",
        "COMSPEC", "TEMP", "TMP", "APPDATA", "LOCALAPPDATA", "PROGRAMDATA",
    }
    environment = {key: value for key, value in os.environ.items() if key.upper() in allowed}
    wrapper = ["cmd.exe", "/d", "/c", "mvnw.cmd"] if os.name == "nt" else ["sh", "mvnw"]

    def run(name, arguments):
        result = subprocess.run(arguments, cwd=ROOT, env=environment, capture_output=True)
        (OUTPUT / (name + ".log")).write_bytes(result.stdout + result.stderr)
        print(name + " exit=" + str(result.returncode), flush=True)
        if result.returncode:
            raise RuntimeError(name + " failed; retained log: " + str(OUTPUT / (name + ".log")))

    run("rag-java", wrapper + ["-o", "-B", "-ntp",
        "-Dtest=RagP1OfflineBusinessReplayTest,RagP1IndependentQuerySetTest,KnowledgeRetrievalThresholdTest",
        "test"])
    run("rag-python", [sys.executable, "-B", "-m", "unittest", "discover",
        "-s", "tools/rag-p1", "-p", "test_replay.py"])
    classpath_file = OUTPUT / "dependencies.txt"
    run("classpath", wrapper + ["-o", "-B", "-ntp", "dependency:build-classpath",
        "-DincludeScope=test", "-Dmdep.outputFile=" + str(classpath_file)])
    classpath = str(ROOT / "target/classes") + os.pathsep + classpath_file.read_text().strip()
    extension = ".exe" if os.name == "nt" else ""
    java_home = environment.get("JAVA_HOME")
    java = str(Path(java_home) / ("bin/java" + extension)) if java_home else shutil.which("java")
    javac = str(Path(java_home) / ("bin/javac" + extension)) if java_home else shutil.which("javac")
    if not java or not javac:
        raise RuntimeError("Java 21 JDK is required; set JAVA_HOME")
    classes = OUTPUT / "classes"
    classes.mkdir()
    sources = [
        "tools/rag-p4-2/java/com/kun/aiinterview/knowledge/admission/QuestionContract.java",
        "tools/rag-p4-2/java/com/kun/aiinterview/knowledge/admission/PrimarySemanticGate.java",
        "tools/rag-candidate-gate/java/com/kun/aiinterview/knowledge/admission/experimental/CandidateLevelSemanticGate.java",
        "tools/rag-candidate-gate/CandidateLevelGateOfflineTest.java",
    ]
    run("candidate-compile", [javac, "-encoding", "UTF-8", "-cp", classpath, "-d", str(classes)] + sources)
    run("candidate-gate", [java, "-cp", str(classes) + os.pathsep + classpath,
        "CandidateLevelGateOfflineTest", str(OUTPUT / "candidate-tests.json")])
    summary = {"status": "PASS", "modelCalls": 0, "databaseAccess": False,
        "historicalEvidenceRequired": False,
        "candidateGate": json.loads((OUTPUT / "candidate-tests.json").read_text())}
    (OUTPUT / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print("PUBLIC_OFFLINE=PASS OUTPUT=" + str(OUTPUT), flush=True)


if __name__ == "__main__":
    main()
